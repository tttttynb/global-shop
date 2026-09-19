package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.ProductReview;
import com.bohao.globalshop.entity.ReviewIntelligence;
import com.bohao.globalshop.mapper.ProductReviewMapper;
import com.bohao.globalshop.mapper.ReviewIntelligenceMapper;
import com.bohao.globalshop.service.ProductTranslationService;
import com.bohao.globalshop.service.ReviewIntelligenceService;
import com.bohao.globalshop.service.TranslationService;
import com.bohao.globalshop.vo.ReviewIntelligenceVo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI口碑档案 2.0 实现（Phase 4 - F9）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewIntelligenceServiceImpl implements ReviewIntelligenceService {

    private final ReviewIntelligenceMapper reviewIntelligenceMapper;
    private final ProductReviewMapper productReviewMapper;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TranslationService translationService;
    private final ProductTranslationService productTranslationService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 自注入（@Lazy 打破循环）：保证 translateAsync 走代理触发 @Async，而非同步执行 */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ReviewIntelligenceService self;

    @Value("${app.reputation.enabled:true}")
    private boolean enabled;

    /** 生成档案所需最少评价数（与 AI 评价总结口径一致） */
    @Value("${app.reputation.min-reviews:3}")
    private int minReviews;

    /** 参与分析的评价条数上限（控制 token 成本） */
    private static final int MAX_REVIEWS = 100;

    /** 防抖合并的待重算商品集合 */
    private static final String PENDING_KEY = "ri:pending";

    private static final String MODEL_VERSION = "qwen-plus";

    // ==================== 查询 ====================

    @Override
    public ReviewIntelligenceVo getForProduct(Long productId, String lang) {
        if (!enabled || productId == null) {
            return null;
        }
        String targetLang = normalizeLang(lang);

        // 1. 精确命中该语言档案
        ReviewIntelligence row = selectRow(productId, targetLang);
        if (row != null) {
            return toVo(row, true);
        }

        // 2. 回退中文档案；有中文但缺该语言 → 异步补翻译
        ReviewIntelligence zhRow = "zh".equals(targetLang) ? null : selectRow(productId, "zh");
        if (zhRow != null) {
            self.translateAsync(productId);
            return toVo(zhRow, false);
        }

        // 3. 连中文都没有：评价达标则现场生成一次（同步，秒级）
        long reviewCount = countReviews(productId);
        if (reviewCount < minReviews) {
            return null;
        }
        ReviewIntelligence generated = generate(productId);
        if (generated == null) {
            return null;
        }
        if (!"zh".equals(targetLang)) {
            self.translateAsync(productId);
            // 翻译未完成前先回退中文
            return toVo(generated, false);
        }
        return toVo(generated, true);
    }

    // ==================== 增量重算（防抖合并） ====================

    @Override
    public void markPending(Long productId) {
        if (!enabled || productId == null) {
            return;
        }
        stringRedisTemplate.opsForSet().add(PENDING_KEY, String.valueOf(productId));
    }

    @Override
    public int flushPending() {
        if (!enabled) {
            return 0;
        }
        int processed = 0;
        for (int i = 0; i < 20; i++) {
            String popped = stringRedisTemplate.opsForSet().pop(PENDING_KEY);
            if (popped == null) {
                break;
            }
            try {
                Long productId = Long.valueOf(popped);
                if (countReviews(productId) >= minReviews) {
                    generate(productId);
                    processed++;
                }
            } catch (Exception e) {
                log.error("口碑档案重算失败: productId={}", popped, e);
            }
        }
        if (processed > 0) {
            log.info("🧠 口碑档案批量重算完成: {} 个商品", processed);
        }
        return processed;
    }

    // ==================== 生成 ====================

    /** 生成中文档案并落库，随后异步翻译多语言版本 */
    private ReviewIntelligence generate(Long productId) {
        QueryWrapper<ProductReview> qw = new QueryWrapper<>();
        qw.eq("product_id", productId).orderByDesc("create_time").last("LIMIT " + MAX_REVIEWS);
        List<ProductReview> reviews = productReviewMapper.selectList(qw);
        if (reviews.size() < minReviews) {
            return null;
        }

        StringBuilder reviewText = new StringBuilder();
        for (ProductReview r : reviews) {
            reviewText.append("- [评分: ").append(r.getRating()).append("/5] ");
            reviewText.append(r.getContent() != null ? r.getContent() : "（无文字）");
            reviewText.append("\n");
        }

        String prompt = """
                你是电商口碑分析专家。请分析以下商品评价，生成结构化的"买家口碑档案"。

                要求：
                1. pros: 优点Top3（每条10字以内，短语）
                2. cons: 缺点Top3（每条10字以内；无明显缺点则 ["暂无明显缺点"]）
                3. bestFor: 适合人群一句话（20字以内）
                4. recommendScore: 综合推荐度（0-100 整数，综合评分分布与情感倾向）
                5. impressions: 买家印象标签 4-8 个：tag 为 2-6 字短语，sentiment 取 pos/neg，count 为该印象被提及的 approximate 次数
                6. summary: 综合口碑总结（60字以内）

                只返回 JSON，不要 markdown 代码块标记：
                {"pros":["..."],"cons":["..."],"bestFor":"...","recommendScore":85,"impressions":[{"tag":"物流快","sentiment":"pos","count":5}],"summary":"..."}

                === 商品评价（共 %d 条） ===
                %s
                """.formatted(reviews.size(), reviewText.toString());

        try {
            String llmResponse = chatModel.chat(UserMessage.from(prompt)).aiMessage().text();
            String json = stripMarkdown(llmResponse);
            IntelligenceContent content = objectMapper.readValue(json, IntelligenceContent.class);
            if (content.getPros() == null || content.getPros().isEmpty()) {
                log.warn("口碑档案生成内容为空: productId={}", productId);
                return null;
            }
            ReviewIntelligence row = upsert(productId, "zh",
                    objectMapper.writeValueAsString(content), reviews.size());
            // 中文档案更新后，旧翻译作废 → 异步重翻
            self.translateAsync(productId);
            log.info("🧠 口碑档案生成完成: productId={}, reviews={}, score={}",
                    productId, reviews.size(), content.getRecommendScore());
            return row;
        } catch (Exception e) {
            log.error("口碑档案生成失败: productId={}", productId, e);
            return null;
        }
    }

    /** 复用 F5 翻译链路：中文档案 → en/ja/ko/th 多语言口碑档案 */
    @Override
    @Async
    public void translateAsync(Long productId) {
        if (!enabled || productId == null) {
            return;
        }
        ReviewIntelligence zhRow = selectRow(productId, "zh");
        if (zhRow == null) {
            return;
        }
        try {
            IntelligenceContent zh = objectMapper.readValue(zhRow.getContentJson(), IntelligenceContent.class);
            for (String lang : productTranslationService.getTargetLanguages()) {
                try {
                    IntelligenceContent translated = new IntelligenceContent();
                    translated.setPros(translateList(zh.getPros(), lang));
                    translated.setCons(translateList(zh.getCons(), lang));
                    translated.setBestFor(safeTranslate(zh.getBestFor(), lang));
                    translated.setSummary(safeTranslate(zh.getSummary(), lang));
                    translated.setRecommendScore(zh.getRecommendScore());
                    List<IntelligenceContent.Impression> tags = new ArrayList<>();
                    if (zh.getImpressions() != null) {
                        List<String> tagTexts = zh.getImpressions().stream()
                                .map(IntelligenceContent.Impression::getTag).toList();
                        List<String> translatedTags = translateList(tagTexts, lang);
                        for (int i = 0; i < zh.getImpressions().size(); i++) {
                            IntelligenceContent.Impression imp = new IntelligenceContent.Impression();
                            imp.setTag(i < translatedTags.size() ? translatedTags.get(i) : zh.getImpressions().get(i).getTag());
                            imp.setSentiment(zh.getImpressions().get(i).getSentiment());
                            imp.setCount(zh.getImpressions().get(i).getCount());
                            tags.add(imp);
                        }
                    }
                    translated.setImpressions(tags);
                    upsert(productId, lang, objectMapper.writeValueAsString(translated), zhRow.getReviewCount());
                    log.info("🌍 口碑档案翻译完成: productId={}, lang={}", productId, lang);
                } catch (Exception e) {
                    // 单语言失败不影响其他语言；前台自动回退中文档案
                    log.error("❌ 口碑档案翻译失败: productId={}, lang={}, {}", productId, lang, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("口碑档案翻译解析失败: productId={}", productId, e);
        }
    }

    // ==================== 内部工具 ====================

    private ReviewIntelligence selectRow(Long productId, String lang) {
        return reviewIntelligenceMapper.selectOne(new QueryWrapper<ReviewIntelligence>()
                .eq("product_id", productId)
                .eq("lang", lang)
                .last("LIMIT 1"));
    }

    private long countReviews(Long productId) {
        Long count = productReviewMapper.selectCount(new QueryWrapper<ProductReview>()
                .eq("product_id", productId));
        return count == null ? 0 : count;
    }

    private ReviewIntelligence upsert(Long productId, String lang, String contentJson, int reviewCount) {
        ReviewIntelligence existing = selectRow(productId, lang);
        if (existing == null) {
            ReviewIntelligence row = new ReviewIntelligence();
            row.setProductId(productId);
            row.setLang(lang);
            row.setContentJson(contentJson);
            row.setReviewCount(reviewCount);
            row.setModelVersion(MODEL_VERSION);
            row.setCreateTime(LocalDateTime.now());
            reviewIntelligenceMapper.insert(row);
            return row;
        }
        existing.setContentJson(contentJson);
        existing.setReviewCount(reviewCount);
        existing.setModelVersion(MODEL_VERSION);
        reviewIntelligenceMapper.updateById(existing);
        return existing;
    }

    /** 列表整体翻译：换行拼接 → 一次 LLM 调用 → 按行拆回（词条内约定无换行） */
    private List<String> translateList(List<String> items, String lang) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        String joined = String.join("\n", items);
        String translated = translationService.translate(joined, lang);
        if (translated == null || translated.isBlank()) {
            return items;
        }
        String[] lines = translated.split("\n");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            result.add(i < lines.length && !lines[i].isBlank() ? lines[i].trim() : items.get(i));
        }
        return result;
    }

    private String safeTranslate(String text, String lang) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String translated = translationService.translate(text, lang);
        return translated == null || translated.isBlank() ? text : translated;
    }

    private String stripMarkdown(String raw) {
        String json = raw.trim();
        if (json.startsWith("```json")) json = json.substring(7);
        if (json.startsWith("```")) json = json.substring(3);
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        return json.trim();
    }

    private String normalizeLang(String lang) {
        if (lang == null || lang.isBlank() || lang.toLowerCase().startsWith("zh")) {
            return "zh";
        }
        String l = lang.trim().toLowerCase();
        int idx = l.indexOf('-');
        return idx > 0 ? l.substring(0, idx) : l;
    }

    private ReviewIntelligenceVo toVo(ReviewIntelligence row, boolean exactLang) {
        try {
            IntelligenceContent content = objectMapper.readValue(row.getContentJson(), IntelligenceContent.class);
            ReviewIntelligenceVo vo = new ReviewIntelligenceVo();
            vo.setProductId(row.getProductId());
            vo.setLang(row.getLang());
            vo.setPros(content.getPros());
            vo.setCons(content.getCons());
            vo.setBestFor(content.getBestFor());
            vo.setRecommendScore(content.getRecommendScore());
            vo.setSummary(content.getSummary());
            vo.setReviewCount(row.getReviewCount());
            vo.setUpdateTime(row.getUpdateTime() != null ? row.getUpdateTime() : row.getCreateTime());
            vo.setExactLang(exactLang);
            List<ReviewIntelligenceVo.ImpressionTag> tags = new ArrayList<>();
            if (content.getImpressions() != null) {
                for (IntelligenceContent.Impression imp : content.getImpressions()) {
                    ReviewIntelligenceVo.ImpressionTag tag = new ReviewIntelligenceVo.ImpressionTag();
                    tag.setTag(imp.getTag());
                    tag.setSentiment(imp.getSentiment());
                    tag.setCount(imp.getCount());
                    tags.add(tag);
                }
            }
            vo.setImpressions(tags);
            return vo;
        } catch (Exception e) {
            log.error("口碑档案解析失败: id={}", row.getId(), e);
            return null;
        }
    }

    /** content_json 的结构化载体 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IntelligenceContent {
        private List<String> pros;
        private List<String> cons;
        private String bestFor;
        private Integer recommendScore;
        private List<Impression> impressions;
        private String summary;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Impression {
            private String tag;
            private String sentiment;
            private Integer count;
        }
    }
}
