package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.dto.HomeFeedDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.mapper.UserProfileMapper;
import com.bohao.globalshop.service.RecommendationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化推荐服务实现（Tier 2.1c）
 * <p>
 * 基于用户偏好品类和价格带，用 MySQL 查询推荐商品。
 * 无画像用户降级为全品类销量排序。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final UserProfileMapper userProfileMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<HomeFeedDto.HomeSection.ProductItem> getYouMayLike(Long userId, int size) {
        List<Long> preferredCategoryIds = null;
        BigDecimal priceMin = null;
        BigDecimal priceMax = null;

        // 1. 尝试获取用户画像
        if (userId != null) {
            try {
                QueryWrapper<UserProfile> qw = new QueryWrapper<>();
                qw.eq("user_id", userId);
                UserProfile profile = userProfileMapper.selectOne(qw);

                if (profile != null) {
                    // 解析偏好品类
                    if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
                        Map<String, Double> catWeights = objectMapper.readValue(
                                profile.getPreferredCategories(),
                                new TypeReference<Map<String, Double>>() {});
                        preferredCategoryIds = catWeights.keySet().stream()
                                .map(Long::valueOf)
                                .limit(3) // 只取 top 3 偏好品类
                                .collect(Collectors.toList());
                    }
                    // 价格带
                    priceMin = profile.getPriceRangeMin();
                    priceMax = profile.getPriceRangeMax();
                }
            } catch (Exception e) {
                log.debug("用户画像读取失败，降级为全品类推荐: userId={}", userId, e);
            }
        }

        // 2. 构建查询
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1);

        // 品类过滤：只取偏好品类
        if (preferredCategoryIds != null && !preferredCategoryIds.isEmpty()) {
            qw.in("category_id", preferredCategoryIds);
        }

        // 价格带过滤（有合理范围时）
        if (priceMin != null && priceMax != null
                && priceMax.compareTo(priceMin) > 0
                && priceMax.compareTo(BigDecimal.ZERO) > 0) {
            qw.between("price", priceMin, priceMax);
        }

        // 按销量排序
        qw.orderByDesc("sales_count");
        qw.last("LIMIT " + size * 2); // 多取一些用于去重补位

        List<Product> products = productMapper.selectList(qw);
        Set<Long> returnedIds = new HashSet<>();

        List<Product> result = new ArrayList<>();
        for (Product p : products) {
            if (result.size() >= size) break;
            result.add(p);
            returnedIds.add(p.getId());
        }

        // 3. 如果结果不足 size，用全品类销量 top 补齐
        if (result.size() < size) {
            QueryWrapper<Product> backfillQw = new QueryWrapper<>();
            backfillQw.eq("status", 1);
            if (!returnedIds.isEmpty()) {
                backfillQw.notIn("id", returnedIds);
            }
            backfillQw.orderByDesc("sales_count");
            backfillQw.last("LIMIT " + (size - result.size()));

            List<Product> backfill = productMapper.selectList(backfillQw);
            result.addAll(backfill);
        }

        // 4. 批量查店铺名
        Set<Long> shopIds = result.stream()
                .map(Product::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> shopNameMap = shopIds.isEmpty() ? Map.of()
                : shopMapper.selectBatchIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Shop::getName, (a, b) -> a));

        // 5. 映射为 ProductItem
        return result.stream().map(p -> {
            HomeFeedDto.HomeSection.ProductItem item = new HomeFeedDto.HomeSection.ProductItem();
            item.setId(p.getId());
            item.setName(p.getName());
            item.setPrice(p.getPrice());
            item.setCoverImage(p.getCoverImage());
            item.setShopName(shopNameMap.getOrDefault(p.getShopId(), "平台自营"));
            item.setSalesCount(p.getSalesCount() != null ? p.getSalesCount() : 0);
            item.setTag(computeTag(p));
            return item;
        }).collect(Collectors.toList());
    }

    private String computeTag(Product p) {
        if (p.getSalesCount() != null && p.getSalesCount() > 100) return "爆款";
        if (p.getCreateTime() != null &&
                p.getCreateTime().isAfter(java.time.LocalDateTime.now().minusDays(7)))
            return "新品";
        if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.valueOf(100)) < 0) return "超值";
        return null;
    }
}
