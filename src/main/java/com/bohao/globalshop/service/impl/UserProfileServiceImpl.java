package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.entity.TradeOrderItem;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.enums.UserTier;
import com.bohao.globalshop.mapper.ProductFavoriteMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductReviewMapper;
import com.bohao.globalshop.mapper.TradeOrderItemMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.mapper.UserProfileMapper;
import com.bohao.globalshop.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户消费画像服务实现
 * <p>
 * 缓存架构：Caffeine L1（30min） → Redis Hash L2（1h） → MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileMapper userProfileMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ProductMapper productMapper;
    private final ProductReviewMapper productReviewMapper;
    private final ProductFavoriteMapper productFavoriteMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<Long, UserProfile> profileLocalCache;
    private final ObjectMapper objectMapper;

    // ==================== Redis Key 常量 ====================

    private static final String PROFILE_HASH_PREFIX = "user:profile:";
    private static final String TIER_KEY_PREFIX = "user:tier:";
    private static final String PRICE_RANGE_KEY_PREFIX = "user:priceRange:";

    // ==================== 缓存 TTL ====================

    private static final long PROFILE_REDIS_TTL_HOURS = 1;
    private static final long TIER_REDIS_TTL_HOURS = 24;
    private static final long PRICE_RANGE_REDIS_TTL_HOURS = 24;

    // ==================== 价格带计算参数 ====================

    /** 时间衰减因子 α */
    private static final double DECAY_ALPHA = 0.7;
    /** 价格带下限系数 */
    private static final double PRICE_MIN_RATIO = 0.6;
    /** 价格带上限系数 */
    private static final double PRICE_MAX_RATIO = 1.4;

    // ==================== 公开方法 ====================

    @Override
    public UserProfile getProfile(Long userId) {
        // L1: Caffeine
        UserProfile cached = profileLocalCache.getIfPresent(userId);
        if (cached != null) {
            log.debug("画像 L1 缓存命中: userId={}", userId);
            return cached;
        }

        // L2: Redis
        UserProfile fromRedis = loadFromRedis(userId);
        if (fromRedis != null) {
            log.debug("画像 L2 缓存命中: userId={}", userId);
            profileLocalCache.put(userId, fromRedis);
            return fromRedis;
        }

        // L3: MySQL
        UserProfile fromDb = loadOrCreateFromDb(userId);
        saveToRedis(userId, fromDb);
        profileLocalCache.put(userId, fromDb);
        log.debug("画像从数据库加载: userId={}", userId);
        return fromDb;
    }

    @Override
    public void updateOnOrderComplete(Long userId, BigDecimal orderAmount) {
        UserProfile profile = loadOrCreateFromDb(userId);

        // 1. 更新累计指标
        BigDecimal oldTotal = profile.getTotalSpent() != null ? profile.getTotalSpent() : BigDecimal.ZERO;
        profile.setTotalSpent(oldTotal.add(orderAmount));

        int oldCount = profile.getTotalOrderCount() != null ? profile.getTotalOrderCount() : 0;
        profile.setTotalOrderCount(oldCount + 1);

        // 2. 更新平均客单价
        profile.setAvgOrderValue(profile.getTotalSpent()
                .divide(BigDecimal.valueOf(profile.getTotalOrderCount()), 2, RoundingMode.HALF_UP));

        // 3. 更新最高单笔消费
        if (profile.getMaxSingleOrder() == null || orderAmount.compareTo(profile.getMaxSingleOrder()) > 0) {
            profile.setMaxSingleOrder(orderAmount);
        }

        // 4. 重新计算用户层级
        profile.setUserTier(classifyTier(profile));

        // 5. 重新计算价格带
        calculateAndSetPriceRange(profile, userId);

        // 6. 更新最近下单时间
        profile.setLastOrderTime(LocalDateTime.now());

        // 7. 刷新活跃度评分
        profile.setActivityScore(computeActivityScore(userId));

        // 8. 持久化
        saveOrUpdate(profile);

        // 9. 刷新缓存
        refreshCache(userId, profile);

        log.info("用户画像已更新: userId={}, tier={}, totalSpent={}, avgOrder={}, orderAmount={}",
                userId, profile.getUserTier(), profile.getTotalSpent(), profile.getAvgOrderValue(), orderAmount);
    }

    @Override
    public void recalculateProfile(Long userId) {
        UserProfile profile = loadOrCreateFromDb(userId);

        // 统计所有已成交订单（status = 4 或 5）
        QueryWrapper<TradeOrder> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).in("status", Arrays.asList(4, 5));
        List<TradeOrder> completedOrders = traderOrderMapper.selectList(qw);

        if (completedOrders.isEmpty()) {
            // 无成交记录，重置为基础画像
            resetToDefault(profile);
        } else {
            BigDecimal totalSpent = completedOrders.stream()
                    .map(TradeOrder::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int count = completedOrders.size();

            profile.setTotalSpent(totalSpent);
            profile.setTotalOrderCount(count);
            profile.setAvgOrderValue(totalSpent.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
            profile.setMaxSingleOrder(completedOrders.stream()
                    .map(TradeOrder::getTotalAmount)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO));
            profile.setLastOrderTime(completedOrders.stream()
                    .map(TradeOrder::getCreateTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));

            // 近90天指标
            LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
            List<TradeOrder> recentOrders = completedOrders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().isAfter(ninetyDaysAgo))
                    .collect(Collectors.toList());
            profile.setRecent90dOrderCount(recentOrders.size());
            profile.setRecent90dSpent(recentOrders.stream()
                    .map(TradeOrder::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        profile.setUserTier(classifyTier(profile));
        calculateAndSetPriceRange(profile, userId);
        profile.setPreferredCategories(computePreferredCategories(userId));
        profile.setActivityScore(computeActivityScore(userId));

        saveOrUpdate(profile);
        refreshCache(userId, profile);

        log.info("用户画像全量重算完成: userId={}, tier={}", userId, profile.getUserTier());
    }

    @Override
    public void batchUpdateRecentMetrics() {
        log.info("开始批量更新用户近90天指标...");
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);

        // 查询所有有画像记录的用户
        List<UserProfile> allProfiles = userProfileMapper.selectList(null);
        int updatedCount = 0;

        for (UserProfile profile : allProfiles) {
            try {
                Long userId = profile.getUserId();

                // 统计近90天已成交订单
                QueryWrapper<TradeOrder> qw = new QueryWrapper<>();
                qw.eq("user_id", userId)
                        .in("status", Arrays.asList(4, 5))
                        .ge("create_time", ninetyDaysAgo);
                List<TradeOrder> recentOrders = traderOrderMapper.selectList(qw);

                profile.setRecent90dOrderCount(recentOrders.size());
                profile.setRecent90dSpent(recentOrders.stream()
                        .map(TradeOrder::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

                // 重新判定层级（因为90天指标可能影响分层）
                String oldTier = profile.getUserTier();
                profile.setUserTier(classifyTier(profile));

                // 更新偏好品类
                profile.setPreferredCategories(computePreferredCategories(userId));

                // 更新活跃度评分
                profile.setActivityScore(computeActivityScore(userId));

                userProfileMapper.updateById(profile);

                // 刷新缓存
                refreshCache(userId, profile);

                if (!Objects.equals(oldTier, profile.getUserTier())) {
                    log.info("用户层级变更: userId={}, {} → {}", userId, oldTier, profile.getUserTier());
                }

                updatedCount++;
            } catch (Exception e) {
                log.error("更新用户画像失败: userId={}", profile.getUserId(), e);
            }
        }

        log.info("批量更新完成！共更新 {} 个用户画像", updatedCount);
    }

    @Override
    public String classifyTier(UserProfile profile) {
        if (profile == null) {
            return UserTier.NEW.name();
        }
        UserTier tier = UserTier.classify(
                profile.getTotalSpent(),
                profile.getAvgOrderValue(),
                profile.getRecent90dSpent()
        );
        return tier.name();
    }

    @Override
    public void initProfilesForExistingUsers() {
        log.info("开始为存量用户初始化画像...");

        // 查询所有有已成交订单的用户
        QueryWrapper<TradeOrder> qw = new QueryWrapper<>();
        qw.in("status", Arrays.asList(4, 5));
        qw.select("distinct user_id");
        List<TradeOrder> distinctUsers = traderOrderMapper.selectList(qw);
        Set<Long> userIds = distinctUsers.stream()
                .map(TradeOrder::getUserId)
                .collect(Collectors.toSet());

        int count = 0;
        for (Long userId : userIds) {
            try {
                // 检查是否已有画像
                QueryWrapper<UserProfile> profileQw = new QueryWrapper<>();
                profileQw.eq("user_id", userId);
                if (userProfileMapper.selectCount(profileQw) > 0) {
                    continue; // 已有画像，跳过
                }
                recalculateProfile(userId);
                count++;
            } catch (Exception e) {
                log.error("初始化画像失败: userId={}", userId, e);
            }
        }

        log.info("存量用户画像初始化完成！共创建 {} 个画像", count);
    }

    // ==================== 内部方法 ====================

    /**
     * 从数据库加载画像，不存在则创建默认画像
     */
    private UserProfile loadOrCreateFromDb(Long userId) {
        QueryWrapper<UserProfile> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        UserProfile profile = userProfileMapper.selectOne(qw);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            resetToDefault(profile);
        }
        return profile;
    }

    /**
     * 重置为默认画像
     */
    private void resetToDefault(UserProfile profile) {
        profile.setTotalSpent(BigDecimal.ZERO);
        profile.setTotalOrderCount(0);
        profile.setAvgOrderValue(BigDecimal.ZERO);
        profile.setRecent90dSpent(BigDecimal.ZERO);
        profile.setRecent90dOrderCount(0);
        profile.setMaxSingleOrder(BigDecimal.ZERO);
        profile.setUserTier(UserTier.NEW.name());
        profile.setPreferredCategories("{}");
        profile.setPriceRangeMin(null);
        profile.setPriceRangeMax(null);
        profile.setActivityScore(BigDecimal.ZERO);
    }

    /**
     * 持久化：存在则更新，不存在则插入
     */
    private void saveOrUpdate(UserProfile profile) {
        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
    }

    // ==================== 价格带计算 ====================

    /**
     * 加权移动平均计算用户偏好价格带
     * <p>
     * price_center = Σ(price_i × α^days_since) / Σ(α^days_since)
     * price_min = price_center × 0.6
     * price_max = price_center × 1.4
     */
    private void calculateAndSetPriceRange(UserProfile profile, Long userId) {
        // 查所有已成交订单的明细
        QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
        orderQw.eq("user_id", userId).in("status", Arrays.asList(4, 5));
        List<TradeOrder> orders = traderOrderMapper.selectList(orderQw);

        if (orders.isEmpty()) {
            profile.setPriceRangeMin(null);
            profile.setPriceRangeMax(null);
            return;
        }

        double weightSum = 0;
        double weightedPriceSum = 0;

        LocalDateTime now = LocalDateTime.now();
        for (TradeOrder order : orders) {
            QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
            itemQw.eq("order_id", order.getId());
            List<TradeOrderItem> items = tradeOrderItemMapper.selectList(itemQw);

            for (TradeOrderItem item : items) {
                long daysSince = order.getCreateTime() != null
                        ? Duration.between(order.getCreateTime(), now).toDays()
                        : 365;
                double weight = Math.pow(DECAY_ALPHA, Math.max(daysSince, 0));
                double price = item.getPrice().doubleValue();
                weightedPriceSum += price * weight;
                weightSum += weight;
            }
        }

        if (weightSum > 0) {
            double priceCenter = weightedPriceSum / weightSum;
            profile.setPriceRangeMin(BigDecimal.valueOf(priceCenter * PRICE_MIN_RATIO).setScale(2, RoundingMode.HALF_UP));
            profile.setPriceRangeMax(BigDecimal.valueOf(priceCenter * PRICE_MAX_RATIO).setScale(2, RoundingMode.HALF_UP));
        }
    }

    // ==================== 偏好品类计算 ====================

    /**
     * 统计近90天购买品类分布，返回 Top3 归一化权重
     *
     * @return JSON: {"categoryId": weight, ...}
     */
    private String computePreferredCategories(Long userId) {
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);

        QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
        orderQw.eq("user_id", userId)
                .in("status", Arrays.asList(4, 5))
                .ge("create_time", ninetyDaysAgo);
        List<TradeOrder> recentOrders = traderOrderMapper.selectList(orderQw);

        if (recentOrders.isEmpty()) {
            return "{}";
        }

        // categoryId → 购买次数
        Map<Long, Integer> categoryCount = new HashMap<>();
        for (TradeOrder order : recentOrders) {
            QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
            itemQw.eq("order_id", order.getId());
            List<TradeOrderItem> items = tradeOrderItemMapper.selectList(itemQw);

            for (TradeOrderItem item : items) {
                Long productId = item.getProductId();
                var product = productMapper.selectById(productId);
                if (product != null && product.getCategoryId() != null) {
                    categoryCount.merge(product.getCategoryId(), item.getQuantity(), (a, b) -> a + b);
                }
            }
        }

        // 取 Top 3 并归一化
        int total = categoryCount.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> result = categoryCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> total > 0 ? (double) e.getValue() / total : 0.0,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("序列化偏好品类失败: userId={}", userId, e);
            return "{}";
        }
    }

    // ==================== 活跃度评分 ====================

    /**
     * 综合评分 (0-100)：
     * - 近90天订单数：最多 40 分
     * - 近90天评价数：最多 30 分
     * - 近90天收藏数：最多 30 分
     */
    private BigDecimal computeActivityScore(Long userId) {
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);

        // 近90天已成交订单数
        QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
        orderQw.eq("user_id", userId)
                .in("status", Arrays.asList(4, 5))
                .ge("create_time", ninetyDaysAgo);
        Long orderCountLong = traderOrderMapper.selectCount(orderQw);
        int orderCount = orderCountLong != null ? orderCountLong.intValue() : 0;

        // 近90天评价数
        QueryWrapper<com.bohao.globalshop.entity.ProductReview> reviewQw = new QueryWrapper<>();
        reviewQw.eq("user_id", userId).ge("create_time", ninetyDaysAgo);
        Long reviewCountLong = productReviewMapper.selectCount(reviewQw);
        int reviewCount = reviewCountLong != null ? reviewCountLong.intValue() : 0;

        // 近90天收藏数
        QueryWrapper<com.bohao.globalshop.entity.ProductFavorite> favQw = new QueryWrapper<>();
        favQw.eq("user_id", userId).ge("create_time", ninetyDaysAgo);
        Long favCountLong = productFavoriteMapper.selectCount(favQw);
        int favCount = favCountLong != null ? favCountLong.intValue() : 0;

        // 订单分：每单 8 分，最多 40 分
        double orderScore = Math.min(orderCount * 8.0, 40.0);
        // 评价分：每条 6 分，最多 30 分
        double reviewScore = Math.min(reviewCount * 6.0, 30.0);
        // 收藏分：每个 3 分，最多 30 分
        double favScore = Math.min(favCount * 3.0, 30.0);

        double totalScore = orderScore + reviewScore + favScore;
        return BigDecimal.valueOf(Math.min(totalScore, 100.0)).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 缓存操作 ====================

    private void refreshCache(Long userId, UserProfile profile) {
        // 更新 L1 Caffeine
        profileLocalCache.put(userId, profile);
        // 更新 L2 Redis
        saveToRedis(userId, profile);
        // 更新 Redis 独立缓存
        stringRedisTemplate.opsForValue().set(
                TIER_KEY_PREFIX + userId,
                profile.getUserTier(),
                TIER_REDIS_TTL_HOURS, TimeUnit.HOURS);
        if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
            stringRedisTemplate.opsForValue().set(
                    PRICE_RANGE_KEY_PREFIX + userId,
                    profile.getPriceRangeMin() + "-" + profile.getPriceRangeMax(),
                    PRICE_RANGE_REDIS_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    private void saveToRedis(Long userId, UserProfile profile) {
        String key = PROFILE_HASH_PREFIX + userId;
        Map<String, String> hash = new HashMap<>();
        hash.put("userId", String.valueOf(userId));
        hash.put("totalSpent", profile.getTotalSpent() != null ? profile.getTotalSpent().toString() : "0");
        hash.put("totalOrderCount", String.valueOf(profile.getTotalOrderCount() != null ? profile.getTotalOrderCount() : 0));
        hash.put("avgOrderValue", profile.getAvgOrderValue() != null ? profile.getAvgOrderValue().toString() : "0");
        hash.put("recent90dSpent", profile.getRecent90dSpent() != null ? profile.getRecent90dSpent().toString() : "0");
        hash.put("recent90dOrderCount", String.valueOf(profile.getRecent90dOrderCount() != null ? profile.getRecent90dOrderCount() : 0));
        hash.put("maxSingleOrder", profile.getMaxSingleOrder() != null ? profile.getMaxSingleOrder().toString() : "0");
        hash.put("userTier", profile.getUserTier() != null ? profile.getUserTier() : UserTier.NEW.name());
        hash.put("priceRangeMin", profile.getPriceRangeMin() != null ? profile.getPriceRangeMin().toString() : "");
        hash.put("priceRangeMax", profile.getPriceRangeMax() != null ? profile.getPriceRangeMax().toString() : "");
        hash.put("activityScore", profile.getActivityScore() != null ? profile.getActivityScore().toString() : "0");

        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, PROFILE_REDIS_TTL_HOURS, TimeUnit.HOURS);
    }

    private UserProfile loadFromRedis(Long userId) {
        String key = PROFILE_HASH_PREFIX + userId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setTotalSpent(toBigDecimal(entries, "totalSpent"));
        profile.setTotalOrderCount(toInteger(entries, "totalOrderCount"));
        profile.setAvgOrderValue(toBigDecimal(entries, "avgOrderValue"));
        profile.setRecent90dSpent(toBigDecimal(entries, "recent90dSpent"));
        profile.setRecent90dOrderCount(toInteger(entries, "recent90dOrderCount"));
        profile.setMaxSingleOrder(toBigDecimal(entries, "maxSingleOrder"));
        profile.setUserTier(toString(entries, "userTier"));
        profile.setPriceRangeMin(toBigDecimal(entries, "priceRangeMin"));
        profile.setPriceRangeMax(toBigDecimal(entries, "priceRangeMax"));
        profile.setActivityScore(toBigDecimal(entries, "activityScore"));
        return profile;
    }

    private BigDecimal toBigDecimal(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || val.toString().isEmpty()) return null;
        return new BigDecimal(val.toString());
    }

    private Integer toInteger(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || val.toString().isEmpty()) return 0;
        return Integer.valueOf(val.toString());
    }

    private String toString(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
