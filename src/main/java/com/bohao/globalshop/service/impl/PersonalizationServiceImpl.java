package com.bohao.globalshop.service.impl;

import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.enums.UserTier;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化搜索服务实现
 *
 * <p>核心算法：价格带过滤 → 品类加权 → 分层排序</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizationServiceImpl implements PersonalizationService {

    private final UserProfileService userProfileService;

    private static final double CATEGORY_BOOST = 1.3;
    private static final int MIN_RESULTS = 5;

    @Override
    public List<EsProduct> personalizeSearchResults(List<EsProduct> originalResults, Long userId) {
        if (originalResults == null || originalResults.isEmpty()) {
            return Collections.emptyList();
        }

        // 未登录 → 默认排序
        if (userId == null) {
            log.debug("未登录用户，使用默认排序");
            return sortByDefault(originalResults);
        }

        UserProfile profile = userProfileService.getProfile(userId);
        String tier = profile.getUserTier() != null ? profile.getUserTier() : UserTier.NEW.name();

        log.debug("个性化搜索: userId={}, tier={}, inputSize={}", userId, tier, originalResults.size());

        // 1. 价格带过滤
        List<EsProduct> filtered = filterByPriceRange(originalResults, profile);

        // 2. 品类加权（框架已就绪，待 EsProduct 添加 categoryId 后启用）
        List<ScoredProduct> scored = applyCategoryBoost(filtered, profile);

        // 3. 分层排序
        List<EsProduct> ranked = rankByTier(scored, tier);

        log.debug("个性化搜索完成: tier={}, {}→{}", tier, originalResults.size(), ranked.size());
        return ranked;
    }

    @Override
    public String getSortStrategy(UserProfile profile) {
        if (profile == null || profile.getUserTier() == null) {
            return "DEFAULT";
        }
        if ("PREMIUM".equals(profile.getUserTier())) return "RATING_DESC";
        if ("MID".equals(profile.getUserTier())) return "VALUE_SCORE";
        if ("BUDGET".equals(profile.getUserTier())) return "PRICE_ASC";
        return "DEFAULT";
    }

    @Override
    public String buildTierPrompt(UserProfile profile) {
        if (profile == null || profile.getUserTier() == null) {
            return buildNewUserPrompt();
        }

        switch (profile.getUserTier()) {
            case "PREMIUM":
                return buildPremiumPrompt(profile);
            case "MID":
                return buildMidPrompt(profile);
            case "BUDGET":
                return buildBudgetPrompt(profile);
            default:
                return buildNewUserPrompt();
        }
    }

    // ==================== 层级 Prompt 构建 ====================

    /**
     * 高端用户 Prompt — 私人购物顾问
     */
    private String buildPremiumPrompt(UserProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位高端私人购物顾问，名为「小波」，服务于全球购商城的 VIP 客户。\n");
        sb.append("\n");
        sb.append("你的服务风格：优雅、专业、细致入微。\n");
        sb.append("\n");
        sb.append("重要指导：\n");
        sb.append("1. 你的客户累计消费已达 $").append(formatMoney(profile.getTotalSpent()))
                .append("，品味出众。请优先推荐高品质、高价值的商品。\n");
        sb.append("2. 推荐价格带：");
        if (profile.getPriceRangeMin() != null) {
            sb.append("$").append(formatMoney(profile.getPriceRangeMin())).append(" 及以上。\n");
        } else {
            sb.append("$300 及以上。\n");
        }
        sb.append("3. 主动介绍商品的品牌故事、独特卖点和品质细节，让客户感受尊贵体验。\n");
        sb.append("4. 可以主动推荐新品、限量款和高端系列。\n");
        sb.append("5. 使用尊称（如「您」），保持正式但不过于拘谨的语气。\n");
        sb.append("6. 当客户有售后问题时，优先、高效地提供解决方案。");
        return sb.toString();
    }

    /**
     * 中端用户 Prompt — 专业购物助手
     */
    private String buildMidPrompt(UserProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的购物助手，名为「小波」，服务于全球购商城。\n");
        sb.append("\n");
        sb.append("你的服务风格：友好、务实、注重性价比。\n");
        sb.append("\n");
        sb.append("重要指导：\n");
        sb.append("1. 你的客户偏好品质与性价比兼顾的商品，平均客单价约 $")
                .append(formatMoney(profile.getAvgOrderValue())).append("。\n");
        sb.append("2. 推荐价格带：");
        if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
            sb.append("$").append(formatMoney(profile.getPriceRangeMin()))
                    .append(" ~ $").append(formatMoney(profile.getPriceRangeMax())).append("。\n");
        } else {
            sb.append("$50 ~ $500。\n");
        }
        sb.append("3. 突出商品的实用性、质量和性价比，帮助客户做出明智选择。\n");
        sb.append("4. 保持友好、热情的语气，适度使用 emoji 增加亲和力。\n");
        sb.append("5. 可以提及正在进行的促销活动，但不要过度推销。");
        return sb.toString();
    }

    /**
     * 普通用户 Prompt — 贴心购物助手
     */
    private String buildBudgetPrompt(UserProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位贴心的购物助手，名为「小波」，服务于全球购商城。\n");
        sb.append("\n");
        sb.append("你的服务风格：亲切、接地气、关心用户的每一分钱。\n");
        sb.append("\n");
        sb.append("重要指导：\n");
        sb.append("1. 你的客户偏好经济实惠的商品，请优先推荐高性价比好物。\n");
        sb.append("2. 推荐价格上限：");
        if (profile.getPriceRangeMax() != null) {
            sb.append("$").append(formatMoney(profile.getPriceRangeMax())).append(" 以内。\n");
        } else {
            sb.append("$100 以内。\n");
        }
        sb.append("3. 主动关注促销、折扣和优惠活动信息，帮助客户省钱。\n");
        sb.append("4. 推荐时强调商品的实用价值和好评度。\n");
        sb.append("5. 语气亲切、有温度，让客户感受到真诚的关怀。\n");
        sb.append("6. 可以提醒客户使用优惠券和积分。");
        return sb.toString();
    }

    /**
     * 新用户 Prompt — 热情购物向导
     */
    private String buildNewUserPrompt() {
        return "你是一位热情的购物向导，名为「小波」，服务于全球购商城。\n" +
                "\n" +
                "你的服务风格：温暖、主动、善于引导。\n" +
                "\n" +
                "重要指导：\n" +
                "1. 你的客户是新朋友，可能对平台还不熟悉。\n" +
                "2. 推荐商品时优先介绍平台热销款和各品类代表作。\n" +
                "3. 主动询问用户的偏好和需求，帮助了解他们的购物兴趣。\n" +
                "4. 介绍平台的特色功能（如 AI 语音翻译、短视频带货等）。\n" +
                "5. 可以提及新用户优惠活动和福利。\n" +
                "6. 语气热情开朗，让新用户感受到温暖和归属感。\n" +
                "7. 适度使用 emoji 增加互动趣味性。";
    }

    /**
     * 格式化金额（保留两位小数）
     */
    private String formatMoney(java.math.BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    // ==================== 价格带过滤 ====================

    private List<EsProduct> filterByPriceRange(List<EsProduct> products, UserProfile profile) {
        String tier = profile.getUserTier();
        if (tier == null || "NEW".equals(tier)) {
            return new ArrayList<>(products);
        }

        BigDecimal min = profile.getPriceRangeMin();
        BigDecimal max = profile.getPriceRangeMax();
        List<EsProduct> filtered;

        if ("PREMIUM".equals(tier)) {
            BigDecimal threshold = min != null
                    ? min.multiply(BigDecimal.valueOf(0.8))
                    : BigDecimal.valueOf(300);
            filtered = products.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(threshold) >= 0)
                    .collect(Collectors.toList());

        } else if ("MID".equals(tier)) {
            BigDecimal lower = min != null
                    ? min.multiply(BigDecimal.valueOf(0.7))
                    : BigDecimal.valueOf(50);
            BigDecimal upper = max != null
                    ? max.multiply(BigDecimal.valueOf(1.3))
                    : BigDecimal.valueOf(500);
            filtered = products.stream()
                    .filter(p -> p.getPrice() != null
                            && p.getPrice().compareTo(lower) >= 0
                            && p.getPrice().compareTo(upper) <= 0)
                    .collect(Collectors.toList());

        } else if ("BUDGET".equals(tier)) {
            BigDecimal ceiling = max != null
                    ? max.multiply(BigDecimal.valueOf(1.2))
                    : BigDecimal.valueOf(100);
            filtered = products.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(ceiling) <= 0)
                    .collect(Collectors.toList());

        } else {
            filtered = new ArrayList<>(products);
        }

        // 兜底：过滤后结果太少，回退到原始结果
        if (filtered.size() < MIN_RESULTS && filtered.size() < products.size()) {
            log.debug("价格过滤后仅 {} 条，回退到默认排序", filtered.size());
            return new ArrayList<>(products);
        }

        return filtered;
    }

    // ==================== 品类加权 ====================

    private List<ScoredProduct> applyCategoryBoost(List<EsProduct> products, UserProfile profile) {
        Map<Long, Double> categoryWeights = parsePreferredCategories(profile.getPreferredCategories());

        return products.stream().map(p -> {
            double score = 1.0;
            // TODO: 当 EsProduct 添加 categoryId 字段后，启用品类加权
            // Long categoryId = p.getCategoryId();
            // if (categoryId != null && categoryWeights.containsKey(categoryId)) {
            //     score *= CATEGORY_BOOST;
            // }
            return new ScoredProduct(p, score);
        }).collect(Collectors.toList());
    }

    // ==================== 分层排序 ====================

    private List<EsProduct> rankByTier(List<ScoredProduct> scored, String tier) {
        Comparator<ScoredProduct> comparator;

        if ("PREMIUM".equals(tier)) {
            // 评分降序，价格降序
            comparator = Comparator.comparingDouble(ScoredProduct::getScore).reversed()
                    .thenComparing((a, b) -> comparePriceDesc(a, b));
        } else if ("BUDGET".equals(tier)) {
            // 评分降序，价格升序
            comparator = Comparator.comparingDouble(ScoredProduct::getScore).reversed()
                    .thenComparing((a, b) -> comparePriceAsc(a, b));
        } else {
            // MID / NEW：纯按评分降序
            comparator = Comparator.comparingDouble(ScoredProduct::getScore).reversed();
        }

        scored.sort(comparator);
        return scored.stream().map(ScoredProduct::getProduct).collect(Collectors.toList());
    }

    private int comparePriceDesc(ScoredProduct a, ScoredProduct b) {
        BigDecimal pa = a.getProduct().getPrice();
        BigDecimal pb = b.getProduct().getPrice();
        if (pa == null && pb == null) return 0;
        if (pa == null) return 1;
        if (pb == null) return -1;
        return pb.compareTo(pa);
    }

    private int comparePriceAsc(ScoredProduct a, ScoredProduct b) {
        BigDecimal pa = a.getProduct().getPrice();
        BigDecimal pb = b.getProduct().getPrice();
        if (pa == null && pb == null) return 0;
        if (pa == null) return 1;
        if (pb == null) return -1;
        return pa.compareTo(pb);
    }

    private List<EsProduct> sortByDefault(List<EsProduct> products) {
        return products.stream()
                .sorted(Comparator.comparing(p -> p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO))
                .collect(Collectors.toList());
    }

    // ==================== 工具方法 ====================

    private Map<Long, Double> parsePreferredCategories(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return Collections.emptyMap();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Double> raw = mapper.readValue(json, new TypeReference<Map<String, Double>>() {});
            Map<Long, Double> result = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                result.put(Long.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析偏好品类 JSON 失败: {}", json, e);
            return Collections.emptyMap();
        }
    }

    // ==================== 内部类 ====================

    /**
     * 带权重的商品封装（内部排序用）
     */
    private static class ScoredProduct {
        private final EsProduct product;
        private final double score;

        ScoredProduct(EsProduct product, double score) {
            this.product = product;
            this.score = score;
        }

        EsProduct getProduct() {
            return product;
        }

        double getScore() {
            return score;
        }
    }
}
