package com.bohao.globalshop.agent;

import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.common.UserContextHolder;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.UserProfileService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 个性化 Shop 工具集
 * <p>
 * 与 {@link ShopTools} 的区别：通过 {@link UserContextHolder}（ThreadLocal）
 * 自动获取当前登录用户身份，无需依赖 LLM 传入 userId 参数。
 * 搜索结果会根据用户消费层级进行个性化过滤和排序。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedShopTools {

    private final OrderService orderService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingModel embeddingModel;
    private final PersonalizationService personalizationService;
    private final UserProfileService userProfileService;

    /**
     * 查询当前用户的订单列表
     * <p>
     * 用户身份从 ThreadLocal 自动获取，LLM 无需传参。
     * AI 会向用户解释订单状态：0=待支付, 1=已支付, 2=已取消, 3=已发货, 4=已收货, 5=已评价
     * </p>
     */
    @Tool("当用户询问他自己的订单记录、发货状态、物流信息时，调用此工具查询。返回结果后需向用户解释各订单状态。")
    public String getMyOrders() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("PersonalizedShopTools.getMyOrders: 未获取到当前用户ID");
            return "[]";
        }
        log.info("🤖 AI 调用 getMyOrders, userId={}", userId);
        Result<?> result = orderService.getMyOrders(userId);
        return JSONUtil.toJsonStr(result.getData());
    }

    /**
     * 个性化商品搜索
     * <p>
     * 向量语义搜索 + 根据用户消费层级进行价格带过滤和排序。
     * 用户身份从 ThreadLocal 自动获取，搜索结果匹配用户的消费能力。
     * </p>
     */
    @Tool("当用户描述想买什么东西、寻找礼物、需要商品推荐时，调用此工具在商品库中进行个性化搜索。搜索关键词请从用户原话中提炼。")
    public String searchProducts(String keyword) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("🤖 AI 调用 searchProducts, keyword={}, userId={}", keyword, userId);

        try {
            // 1. 向量语义搜索
            List<EsProduct> semanticResults = doSemanticSearch(keyword);

            // 2. 个性化重排（使用 ThreadLocal 中的 userId）
            List<EsProduct> personalized = personalizationService.personalizeSearchResults(semanticResults, userId);

            // 3. 构建对 LLM 友好的返回格式（含价格和层级提示）
            return buildProductSummary(personalized, userId);
        } catch (Exception e) {
            log.error("个性化搜索失败: keyword={}", keyword, e);
            return "[]";
        }
    }

    /**
     * 向量语义搜索（复用 AiSearchController 的逻辑）
     */
    private List<EsProduct> doSemanticSearch(String keyword) {
        float[] userVector = embeddingModel.embed(keyword);
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
                .withMaxResults(10)
                .build();

        SearchHits<EsProduct> hits = elasticsearchOperations.search(query, EsProduct.class);
        List<EsProduct> result = new ArrayList<>();
        for (SearchHit<EsProduct> hit : hits) {
            EsProduct product = hit.getContent();
            log.debug("🎯 命中商品: [{}], 相似度得分: {}", product.getName(), hit.getScore());
            result.add(product);
        }
        return result;
    }

    /**
     * 构建商品摘要信息（含个性化层级提示）
     */
    private String buildProductSummary(List<EsProduct> products, Long userId) {
        if (products == null || products.isEmpty()) {
            return "[]";
        }

        UserProfile profile = null;
        if (userId != null) {
            profile = userProfileService.getProfile(userId);
        }

        List<ProductSummary> summaries = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            EsProduct p = products.get(i);
            ProductSummary s = new ProductSummary();
            s.setRank(i + 1);
            s.setName(p.getName());
            s.setPrice(p.getPrice());
            s.setDescription(p.getDescription());
            s.setShopName(p.getShopName());
            summaries.add(s);
        }

        PersonalizedSearchResult result = new PersonalizedSearchResult();
        result.setProducts(summaries);
        result.setTotalCount(summaries.size());

        if (profile != null && profile.getUserTier() != null) {
            result.setTier(profile.getUserTier());
            result.setTierHint(buildTierHint(profile));
        }

        return JSONUtil.toJsonStr(result);
    }

    private String buildTierHint(UserProfile profile) {
        String tier = profile.getUserTier();
        if ("PREMIUM".equals(tier)) {
            return "已为你精选高品质商品，按评分降序排列";
        } else if ("MID".equals(tier)) {
            return "已为你筛选高性价比商品";
        } else if ("BUDGET".equals(tier)) {
            return "已为你推荐实惠好物，按价格升序排列";
        }
        return "已为你推荐热门商品";
    }

    // ==================== 内部序列化类 ====================

    /**
     * 商品摘要（发给 LLM 的简洁表示）
     */
    public static class ProductSummary {
        private int rank;
        private String name;
        private java.math.BigDecimal price;
        private String description;
        private String shopName;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
    }

    /**
     * 个性化搜索结果包装（含层级提示信息）
     */
    public static class PersonalizedSearchResult {
        private List<ProductSummary> products;
        private int totalCount;
        private String tier;
        private String tierHint;

        public List<ProductSummary> getProducts() { return products; }
        public void setProducts(List<ProductSummary> products) { this.products = products; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getTierHint() { return tierHint; }
        public void setTierHint(String tierHint) { this.tierHint = tierHint; }
    }
}
