package com.bohao.globalshop.agent;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.common.UserContextHolder;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.dto.FrequentlyBoughtDto;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.CartService;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.ProductService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 个性化 Shop 工具集
 * <p>
 * 与 {@link ShopTools} 的区别：通过 {@link UserContextHolder}（ThreadLocal）
 * 自动获取当前登录用户身份，无需依赖 LLM 传入 userId 参数。
 * 搜索结果会根据用户消费层级进行个性化过滤和排序。
 * <p>
 * 🆕 Phase 4 - F10 购物顾问升级：新增 searchByBudget（预算内精选）、
 * getBundleRecommendation（跨品类成套推荐，共现数据 + 语义召回）、
 * batchAddToCart（一键全部加购）；命中商品同步写入 {@link ProductCardCollector}
 * 供前端渲染可加购商品卡片。
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
    private final ProductService productService;
    private final CartService cartService;

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
     * 🆕 预算内精选（Phase 4 - F10）：语义召回 + 价格区间过滤
     */
    @Tool("当用户给出了明确预算（如\"500元以内\"、\"预算2000左右\"）时调用此工具，在预算范围内搜索最匹配的商品。minPrice/maxPrice 传数字字符串，未知一侧传空字符串。")
    public String searchByBudget(String keyword, String minPrice, String maxPrice) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("🤖 AI 调用 searchByBudget, keyword={}, budget=[{}~{}], userId={}", keyword, minPrice, maxPrice, userId);
        try {
            BigDecimal min = parsePrice(minPrice);
            BigDecimal max = parsePrice(maxPrice);
            // 语义召回放大到 30 条，价格过滤后仍保有余量
            List<EsProduct> semanticResults = doSemanticSearch(keyword, 30);
            List<EsProduct> inBudget = new ArrayList<>();
            for (EsProduct p : semanticResults) {
                if (p.getPrice() == null) continue;
                if (min != null && p.getPrice().compareTo(min) < 0) continue;
                if (max != null && p.getPrice().compareTo(max) > 0) continue;
                inBudget.add(p);
                if (inBudget.size() >= 8) break;
            }
            List<EsProduct> personalized = personalizationService.personalizeSearchResults(inBudget, userId);
            if (personalized == null || personalized.isEmpty()) {
                return "{\"products\":[],\"totalCount\":0,\"hint\":\"预算内没有匹配商品，建议用户放宽预算或换个关键词\"}";
            }
            return buildProductSummary(personalized, userId);
        } catch (Exception e) {
            log.error("预算搜索失败: keyword={}", keyword, e);
            return "[]";
        }
    }

    /**
     * 🆕 跨品类成套推荐（Phase 4 - F10）：
     * 有锚点商品 → 复用共现矩阵"买了还买"召回；
     * 纯场景（如"露营装备全家桶"）→ 场景语义召回，跨店铺去重组成套装。
     */
    @Tool("当用户需要一整套/成组商品（如露营装备、健身套装、送礼清单、新生儿用品）时调用此工具做跨品类成套推荐。scene 传场景主题关键词；anchorProductId 传关联的锚点商品ID（没有则传空字符串）。")
    public String getBundleRecommendation(String scene, String anchorProductId) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("🤖 AI 调用 getBundleRecommendation, scene={}, anchor={}, userId={}", scene, anchorProductId, userId);
        try {
            List<EsProduct> bundle = new ArrayList<>();
            Set<Long> seen = new HashSet<>();

            // 1. 锚点商品共现召回（订单共现矩阵，"买了还买"）
            Long anchorId = parseLong(anchorProductId);
            if (anchorId != null) {
                Result<List<FrequentlyBoughtDto>> coResult = productService.getFrequentlyBought(anchorId, 4);
                if (coResult != null && coResult.getCode() == 200 && coResult.getData() != null) {
                    for (FrequentlyBoughtDto dto : coResult.getData()) {
                        if (dto.getId() == null || !seen.add(dto.getId())) continue;
                        EsProduct p = new EsProduct();
                        p.setId(dto.getId());
                        p.setName(dto.getName());
                        p.setPrice(dto.getPrice());
                        p.setCoverImage(dto.getCoverImage());
                        p.setDescription("与锚点商品共同购买 " + dto.getCoOccurrenceCount() + " 次");
                        bundle.add(p);
                    }
                }
            }

            // 2. 场景语义召回补齐（跨店铺 = 跨品类成套）
            if (bundle.size() < 5 && scene != null && !scene.isBlank()) {
                for (EsProduct p : doSemanticSearch(scene, 15)) {
                    if (p.getId() == null || !seen.add(p.getId())) continue;
                    bundle.add(p);
                    if (bundle.size() >= 6) break;
                }
            }
            if (bundle.isEmpty()) {
                return "{\"products\":[],\"totalCount\":0,\"hint\":\"没有找到成套商品，建议用户细化场景描述\"}";
            }

            // 3. 组合总价（一键加购话术需要）
            BigDecimal total = BigDecimal.ZERO;
            for (EsProduct p : bundle) {
                if (p.getPrice() != null) total = total.add(p.getPrice());
            }
            bundle.forEach(this::collectCard);
            String summary = buildProductSummary(bundle, userId);
            // 在 JSON 尾部附上组合价，供 LLM 报"全家桶总价"
            return summary.substring(0, summary.length() - 1)
                    + ",\"bundleTotalPrice\":" + total.toPlainString()
                    + ",\"bundleHint\":\"这是跨品类成套组合，可引导用户一键全部加购\"}";
        } catch (Exception e) {
            log.error("成套推荐失败: scene={}", scene, e);
            return "[]";
        }
    }

    /**
     * 🆕 一键全部加购（Phase 4 - F10）：解析商品清单批量写入购物车
     */
    @Tool("当用户确认要购买推荐的商品（说\"都要了\"、\"全部加购\"、\"帮我加入购物车\"）时调用此工具。itemsJson 传 JSON 数组字符串，格式：[{\"productId\":1,\"quantity\":1}]，productId 必须来自之前工具返回的真实商品，禁止编造。")
    public String batchAddToCart(String itemsJson) {
        Long userId = UserContextHolder.getCurrentUserId();
        log.info("🤖 AI 调用 batchAddToCart, items={}, userId={}", itemsJson, userId);
        if (userId == null) {
            return "{\"success\":false,\"message\":\"未获取到登录用户，无法加购\"}";
        }
        try {
            JSONArray items = JSONUtil.parseArray(itemsJson);
            int success = 0;
            List<String> failed = new ArrayList<>();
            for (Object obj : items) {
                JSONObject item = (JSONObject) obj;
                CartAddDto dto = new CartAddDto();
                dto.setProductId(item.getLong("productId"));
                dto.setSkuId(item.getLong("skuId"));
                Integer quantity = item.getInt("quantity");
                dto.setQuantity(quantity == null || quantity < 1 ? 1 : quantity);
                try {
                    Result<String> r = cartService.addToCart(userId, dto);
                    if (r.getCode() == 200) {
                        success++;
                    } else {
                        failed.add(dto.getProductId() + ":" + r.getMessage());
                    }
                } catch (Exception e) {
                    failed.add(dto.getProductId() + ":" + e.getMessage());
                }
            }
            JSONObject result = new JSONObject();
            result.set("success", success > 0);
            result.set("addedCount", success);
            result.set("failed", failed);
            result.set("message", success > 0 ? "已将 " + success + " 件商品加入购物车" : "加购失败");
            return result.toString();
        } catch (Exception e) {
            log.error("批量加购解析失败: {}", itemsJson, e);
            return "{\"success\":false,\"message\":\"加购清单格式错误\"}";
        }
    }

    /**
     * 向量语义搜索（复用 AiSearchController 的逻辑）
     */
    private List<EsProduct> doSemanticSearch(String keyword) {
        return doSemanticSearch(keyword, 10);
    }

    private List<EsProduct> doSemanticSearch(String keyword, int maxResults) {
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
                .withMaxResults(maxResults)
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
     * <p>🆕 Phase 4 - F10：摘要带上商品ID与主图（LLM 加购需要真实 ID），
     * 同时写入 ProductCardCollector 供前端渲染可加购卡片。
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
            s.setId(p.getId());
            s.setName(p.getName());
            s.setPrice(p.getPrice());
            s.setDescription(p.getDescription());
            s.setShopName(p.getShopName());
            s.setCoverImage(p.getCoverImage());
            summaries.add(s);
            collectCard(p);
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

    /** 命中商品写入 ThreadLocal 收集器（ChatController 随回复返回前端） */
    private void collectCard(EsProduct p) {
        if (p == null || p.getId() == null) {
            return;
        }
        ProductCardCollector.Card card = new ProductCardCollector.Card();
        card.setId(p.getId());
        card.setName(p.getName());
        card.setPrice(p.getPrice());
        card.setCoverImage(p.getCoverImage());
        card.setShopName(p.getShopName());
        ProductCardCollector.add(card);
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
        private Long id;
        private String name;
        private java.math.BigDecimal price;
        private String description;
        private String shopName;
        private String coverImage;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
        public String getCoverImage() { return coverImage; }
        public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
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
