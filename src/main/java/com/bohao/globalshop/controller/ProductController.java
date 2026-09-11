package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.common.UserContextHolder;
import com.bohao.globalshop.dto.AiReviewSummaryDto;
import com.bohao.globalshop.dto.FrequentlyBoughtDto;
import com.bohao.globalshop.dto.HomeFeedDto;
import com.bohao.globalshop.dto.ProductStatsDto;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.repository.EsProductRepository;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.ProductService;
import com.bohao.globalshop.service.RecommendationService;
import com.bohao.globalshop.vo.ProductVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;
    private final EsProductRepository esProductRepository;
    private final ProductMapper productMapper;
    private final PersonalizationService personalizationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RecommendationService recommendationService;
    private final com.bohao.globalshop.service.SkuService skuService;
    private final com.bohao.globalshop.service.PriceHistoryService priceHistoryService;


    @GetMapping("/list")
    public Result<List<ProductVo>> getList() {
        return productService.getProductListWithShop();
    }

    @GetMapping("/list/paged")
    public Result<Map<String, Object>> getListPaged(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return productService.getProductListPaged(categoryId, sort, page, size);
    }

    @PostMapping("/favorite/{id}")
    public Result<String> toggleFavorite(HttpServletRequest request, @PathVariable("id") Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return productService.toggleFavorite(userId, productId);
    }

    @GetMapping("/favorites")
    public Result<List<ProductVo>> getFavorites(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return productService.getFavorites(userId);
    }

    // 获取商品的评价列表
    @GetMapping("/{id}/reviews")
    public Result<List<com.bohao.globalshop.vo.ProductReviewVo>> getProductReviews(@PathVariable("id") Long productId) {
        return productService.getProductReviews(productId);
    }

    /**
     * 🚀 AI 评价总结（Tier 1.1）
     * 聚合所有评论，LLM 生成优缺点/适合人群/综合评分，结果缓存 24h
     */
    @GetMapping("/{id}/ai-summary")
    public Result<AiReviewSummaryDto> getAiReviewSummary(@PathVariable("id") Long productId) {
        return productService.getAiReviewSummary(productId);
    }

    /**
     * 🚀 "看了还看" — ES More Like This 相似商品（Tier 2.1a）
     * 基于商品名称文本相似度，用 ES more_like_this 查询返回相似商品
     */
    @GetMapping("/{id}/similar")
    public Result<List<ProductVo>> getSimilarProducts(
            @PathVariable("id") Long productId,
            @RequestParam(defaultValue = "6") int size) {
        return productService.getSimilarProducts(productId, size);
    }

    /**
     * 🚀 "买了还买" — 订单共现推荐（Tier 2.1b）
     * 基于历史订单共现矩阵，推荐与该商品一起购买频率最高的商品
     */
    @GetMapping("/{id}/frequently-bought")
    public Result<List<FrequentlyBoughtDto>> getFrequentlyBought(
            @PathVariable("id") Long productId,
            @RequestParam(defaultValue = "6") int size) {
        return productService.getFrequentlyBought(productId, size);
    }

    /**
     * 🚀 "猜你喜欢" — 基于用户画像的个性化推荐（Tier 2.1c）
     * 根据用户偏好品类和价格带推荐商品，匿名用户返回全品类销量排序
     */
    @GetMapping("/you-may-like")
    public Result<List<HomeFeedDto.HomeSection.ProductItem>> getYouMayLike(
            @RequestParam(defaultValue = "8") int size) {
        Long userId = UserContextHolder.getCurrentUserId();
        return Result.success(recommendationService.getYouMayLike(userId, size));
    }

    @GetMapping("/detail/{id}")
    public Result<Product> getProductDetail(@PathVariable("id") Long id) {
        // 直接呼叫 Service 层的神级缓存逻辑
        Product product = productService.getProductDetail(id);
        if (product == null) {
            return Result.error(404, "哎呀，商品找不到了！");
        }

        // 🚀 社交证明：记录浏览量（Redis 计数器，15分钟过期滑动窗口）
        try {
            String viewerKey = "product:viewers:" + id;
            String viewerId = UserContextHolder.getCurrentUserId() != null
                    ? "u:" + UserContextHolder.getCurrentUserId()
                    : "ip:anonymous";
            stringRedisTemplate.opsForZSet().add(viewerKey, viewerId, System.currentTimeMillis());
            // 清理 15 分钟前的记录
            long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
            stringRedisTemplate.opsForZSet().removeRangeByScore(viewerKey, 0, cutoff);
            stringRedisTemplate.expire(viewerKey, 30, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // 计数器失败不影响主流程
        }

        return Result.success(product);
    }

    /**
     * 🆕 商品 SKU 列表（Phase 1 - F1 规格系统）
     * 存量单规格商品自动返回懒加载生成的默认 SKU，前端无感兼容。
     */
    @GetMapping("/detail/{id}/skus")
    public Result<List<com.bohao.globalshop.entity.ProductSku>> getProductSkus(@PathVariable("id") Long id) {
        List<com.bohao.globalshop.entity.ProductSku> skus = skuService.listByProductId(id);
        if (skus == null || skus.isEmpty()) {
            com.bohao.globalshop.entity.ProductSku defaultSku = skuService.getOrCreateDefaultSku(id);
            if (defaultSku == null) {
                return Result.error(404, "哎呀，商品找不到了！");
            }
            skus = List.of(defaultSku);
        }
        return Result.success(skus);
    }

    /**
     * 🆕 价格历史走势（Phase 1 - F2）
     * 返回最近 days 天（默认 90 天）的价格快照，前端详情页绘制走势图。
     */
    @GetMapping("/{id}/price-history")
    public Result<List<com.bohao.globalshop.entity.PriceHistory>> getPriceHistory(
            @PathVariable("id") Long id,
            @RequestParam(value = "days", defaultValue = "90") Integer days) {
        return Result.success(priceHistoryService.getHistory(id, days));
    }

    /**
     * 🚀 商品实时统计（Tier 1.3 — 社交证明）
     * 返回：当前浏览人数、今日销量、本周销量、累计销量
     */
    @GetMapping("/{id}/stats")
    public Result<ProductStatsDto> getProductStats(@PathVariable("id") Long id) {
        ProductStatsDto stats = new ProductStatsDto();

        try {
            // 当前浏览人数（15分钟窗口内的独立用户数）
            String viewerKey = "product:viewers:" + id;
            long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
            Long viewers = stringRedisTemplate.opsForZSet().count(viewerKey, cutoff, System.currentTimeMillis());
            stats.setViewersCount(viewers != null ? viewers.intValue() : 0);
        } catch (Exception e) {
            stats.setViewersCount(0);
        }

        try {
            // 今日订单数 / 累计销量 → 从 DB 查
            Product product = productMapper.selectById(id);
            if (product != null) {
                stats.setTotalSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);
                // 简化：每周销量按累计的 30% 估算（可后续改为真实统计）
                stats.setWeeklySalesCount(Math.max(1, (int)(stats.getTotalSalesCount() * 0.3)));
                stats.setTodayOrderCount(Math.max(0, (int)(stats.getTotalSalesCount() * 0.05)));
            }
        } catch (Exception ignored) {
            // 查询失败不阻塞
        }

        return Result.success(stats);
    }

    /**
     *  将 MySQL 数据全量同步到 Elasticsearch
     */
    @GetMapping("/sync-es")
    public Result<String> syncToEs() {
        // 1. 从 MySQL 数据库里全量捞出所有上架的商品
        List<Product> mysqlProducts = productMapper.selectList(null);
        if (mysqlProducts == null || mysqlProducts.isEmpty()) {
            return Result.error(400, "MySQL 里没有商品，无需同步！");
        }
        ArrayList<EsProduct> esProductList = new ArrayList<>();

        //2.将MySQL的实体类，转换为ES的文档模型
        for (Product p : mysqlProducts) {
            EsProduct esDoc = new EsProduct();
            esDoc.setId(p.getId());
            esDoc.setName(p.getName());
            esDoc.setDescription(p.getDescription());
            esDoc.setPrice(p.getPrice());
            esDoc.setShopId(p.getShopId());
            esDoc.setCoverImage(p.getCoverImage());

            // 如果以后要接入 AI 向量模型，也是在这里把 p.getName() 发给大模型生成 float[] 塞进 esDoc！

            esProductList.add(esDoc);
        }
        // 3.一键批量保存到 Elasticsearch 索引库中！
        esProductRepository.saveAll(esProductList);
        return Result.success("太牛了！成功将 " + esProductList.size() + " 条商品数据同步至 ES！");
    }

    /**
     * C端全文智能检索接口 (底层走 ES 8.x + IK 分词)
     */
    @GetMapping("/search")
    public Result<List<EsProduct>> searchFromEs(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,// 默认第0页
            @RequestParam(value = "size", defaultValue = "10") int size) {// 默认每页10条
        // 1. 构造分页参数 (Spring Data 的页码是从 0 开始的)
        PageRequest pageRequest = PageRequest.of(page, size);
        // 2. 呼叫 ES Repository 进行全文检索！
        // 逻辑：只要 商品名称 或 商品描述 里匹配到了关键字，就会被秒搜出来！
        Page<EsProduct> searchResult = esProductRepository.findByNameOrDescription(keyword, keyword, pageRequest);
        // 3. 提取查到的数据列表
        List<EsProduct> content = searchResult.getContent();

        // 4. 🚀 个性化重排：根据用户消费层级调整结果
        Long userId = UserContextHolder.getCurrentUserId();
        List<EsProduct> personalized = personalizationService.personalizeSearchResults(content, userId);

        return Result.success(personalized);
    }
}
