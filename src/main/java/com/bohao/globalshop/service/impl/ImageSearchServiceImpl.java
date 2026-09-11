package com.bohao.globalshop.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.common.UserContextHolder;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.service.ImageSearchService;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.vo.ImageSearchResultVo;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 以图搜图实现（Phase 2 - F4，V1 路径）
 * <p>
 * 复用三块现成能力：
 * 1. visionModel（qwen-vl-max，Langchain4jConfig 已有 Bean）做图片结构化理解；
 * 2. EmbeddingModel（text-embedding-v4）+ ES kNN 做语义召回（与 /api/ai/semantic 同款）；
 * 3. PersonalizationService 按用户消费层级重排。
 * </p>
 */
@Slf4j
@Service
public class ImageSearchServiceImpl implements ImageSearchService {

    private static final String VISION_PROMPT =
            "你是一个跨境电商视觉搜索助手。请仔细观察这张图片中的【主体商品】，忽略背景、人物和文字水印。\n" +
            "请为我输出：\n" +
            "1. category: 商品品类（中文，2-6个字，如：连衣裙 / 蓝牙耳机 / 运动鞋）\n" +
            "2. keywords: 3-6个用于电商搜索的精准特征词（中文，如颜色/材质/风格/功能/适用人群）\n" +
            "3. description: 一句话描述这个商品（中文，30字以内，包含最显著的特征）\n" +
            "🚨【极其重要】：必须严格返回如下 JSON，不要任何 Markdown 代码块标记，不要任何多余文字！\n" +
            "{\"category\": \"品类\", \"keywords\": [\"特征1\", \"特征2\"], \"description\": \"一句话描述\"}";

    @Autowired
    @Qualifier("visionModel")
    private ChatModel visionModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private PersonalizationService personalizationService;

    @Override
    public Result<ImageSearchResultVo> searchByImage(byte[] imageBytes, String mimeType) {
        // 1. 图片 → Base64 → qwen-vl 结构化理解
        JSONObject recognized;
        try {
            String base64Image = Base64.encode(imageBytes);
            UserMessage userMessage = UserMessage.from(
                    TextContent.from(VISION_PROMPT),
                    ImageContent.from(base64Image, mimeType != null ? mimeType : "image/jpeg")
            );
            String jsonResponse = visionModel.chat(userMessage).aiMessage().text();
            // 清洗可能的 Markdown 代码块标记
            jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
            log.info("🔍 以图搜图 - 视觉理解结果: {}", jsonResponse);
            recognized = JSONUtil.parseObj(jsonResponse);
        } catch (Exception e) {
            log.error("❌ 图片理解失败", e);
            return Result.error(500, "AI 看图失败了，请换一张更清晰的商品图试试！（" + e.getMessage() + "）");
        }

        String category = recognized.getStr("category", "");
        List<String> keywords = new ArrayList<>();
        try {
            keywords = recognized.getBeanList("keywords", String.class);
        } catch (Exception ignored) {
        }
        String description = recognized.getStr("description", "");
        if (category.isEmpty() && keywords.isEmpty() && description.isEmpty()) {
            return Result.error(500, "AI 没能从图片中识别出商品，试试拍摄更完整的商品主体？");
        }

        // 2. 拼装语义查询文本（品类权重最高，放最前）
        String queryText = String.join(" ",
                category,
                String.join(" ", keywords),
                description).trim();

        // 3. 向量化 → ES kNN 召回（与文本语义搜索同一条链路）
        List<EsProduct> products;
        try {
            float[] userVector = embeddingModel.embed(queryText);
            List<Float> vectorList = new ArrayList<>();
            for (float v : userVector) {
                vectorList.add(v);
            }
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.knn(k -> k
                            .field("vector")
                            .queryVector(vectorList)
                            .numCandidates(50)
                    ))
                    .withMaxResults(12)
                    .build();
            SearchHits<EsProduct> hits = elasticsearchOperations.search(query, EsProduct.class);
            products = new ArrayList<>();
            for (SearchHit<EsProduct> hit : hits) {
                products.add(hit.getContent());
            }
        } catch (Exception e) {
            log.error("❌ 以图搜图向量召回失败", e);
            return Result.error(500, "相似商品检索失败，请先确认已执行商品向量初始化（/api/ai/init-vectors）");
        }

        // 4. 个性化重排（登录用户按消费层级调整顺序）
        Long userId = UserContextHolder.getCurrentUserId();
        products = personalizationService.personalizeSearchResults(products, userId);

        // 5. 组装结果
        ImageSearchResultVo vo = new ImageSearchResultVo();
        vo.setCategory(category);
        vo.setKeywords(keywords);
        vo.setDescription(description);
        vo.setQueryText(queryText);
        vo.setProducts(products);
        log.info("🎯 以图搜图完成: query=[{}] 召回 {} 件商品", queryText, products.size());
        return Result.success(vo);
    }
}
