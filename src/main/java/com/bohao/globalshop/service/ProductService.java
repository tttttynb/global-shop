package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.AiReviewSummaryDto;
import com.bohao.globalshop.dto.FrequentlyBoughtDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.vo.ProductReviewVo;
import com.bohao.globalshop.vo.ProductVo;

import java.util.List;
import java.util.Map;

public interface ProductService {

    Result<List<ProductVo>> getProductListWithShop();

    Result<Map<String, Object>> getProductListPaged(Long categoryId, String sort, Integer page, Integer size);

    Result<List<ProductReviewVo>> getProductReviews(Long productId);

    Product getProductDetail(Long id);

    Result<String> toggleFavorite(Long userId, Long productId);

    Result<List<ProductVo>> getFavorites(Long userId);

    /**
     * AI 评价总结（Tier 1.1）
     * <p>
     * 聚合该商品所有评论，调用 LLM 生成优缺点、适合人群、综合评分。
     * 结果缓存在 Redis 中（24h），评论数 < 5 时不做总结。
     * </p>
     */
    Result<AiReviewSummaryDto> getAiReviewSummary(Long productId);

    /**
     * "看了还看" — ES more_like_this 相似商品推荐（Tier 2.1a）
     * <p>
     * 基于商品名称文本相似度，用 ES more_like_this 查询返回相似商品。
     * </p>
     */
    Result<List<ProductVo>> getSimilarProducts(Long productId, int size);

    /**
     * "买了还买" — 订单共现矩阵推荐（Tier 2.1b）
     * <p>
     * 基于历史订单中的商品共现关系，推荐与该商品一起购买频率最高的商品。
     * 数据由 CoOccurrenceTask 每天凌晨计算并缓存在 Redis ZSet 中。
     * </p>
     */
    Result<List<FrequentlyBoughtDto>> getFrequentlyBought(Long productId, int size);
}
