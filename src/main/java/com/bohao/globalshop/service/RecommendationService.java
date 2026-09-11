package com.bohao.globalshop.service;

import com.bohao.globalshop.dto.HomeFeedDto;

import java.util.List;

/**
 * 个性化推荐服务（Tier 2.1c）
 * <p>
 * 基于用户画像（偏好品类、价格带）生成推荐商品，
 * 不依赖行为追踪数据。
 * </p>
 */
public interface RecommendationService {

    /**
     * "猜你喜欢" — 基于用户画像的个性化推荐
     *
     * @param userId 用户 ID（可为 null，降级为全品类销量排序）
     * @param size   推荐数量
     * @return 推荐商品列表
     */
    List<HomeFeedDto.HomeSection.ProductItem> getYouMayLike(Long userId, int size);
}
