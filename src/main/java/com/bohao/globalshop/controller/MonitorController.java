package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.ChatLogService;
import com.bohao.globalshop.service.UserProfileService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 监控与运维接口（Phase 4）
 * <p>
 * 提供缓存命中率、对话日志统计、画像校验等管理功能
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final Cache<Long, UserProfile> profileLocalCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserProfileService userProfileService;
    private final ChatLogService chatLogService;

    /**
     * 缓存命中率统计
     * <p>
     * 返回 Caffeine L1 缓存的 hit/miss 统计 + Redis 连通性
     */
    @GetMapping("/cache")
    public Result<Map<String, Object>> cacheMetrics() {
        CacheStats stats = profileLocalCache.stats();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("l1Type", "Caffeine");
        metrics.put("l1HitCount", stats.hitCount());
        metrics.put("l1MissCount", stats.missCount());
        metrics.put("l1HitRate", String.format("%.2f%%", stats.hitRate() * 100));
        metrics.put("l1EvictionCount", stats.evictionCount());
        metrics.put("l1AverageLoadPenaltyMs", String.format("%.2f", stats.averageLoadPenalty() / 1_000_000.0));
        metrics.put("l1EstimatedSize", profileLocalCache.estimatedSize());

        // Redis 连通性检测
        try {
            String pong = stringRedisTemplate.getConnectionFactory()
                    .getConnection().ping();
            metrics.put("redisStatus", "PONG".equals(pong) ? "OK" : "DEGRADED");
        } catch (Exception e) {
            metrics.put("redisStatus", "DOWN: " + e.getMessage());
        }

        // Redis 画像相关 key 数量统计
        try {
            Set<String> profileKeys = stringRedisTemplate.keys("user:profile:*");
            Set<String> tierKeys = stringRedisTemplate.keys("user:tier:*");
            metrics.put("redisProfileKeyCount", profileKeys != null ? profileKeys.size() : 0);
            metrics.put("redisTierKeyCount", tierKeys != null ? tierKeys.size() : 0);
        } catch (Exception e) {
            metrics.put("redisKeyCountError", e.getMessage());
        }

        return Result.success(metrics);
    }

    /**
     * 对话日志统计
     * <p>
     * 按用户层级汇总对话数据，评估个性化效果
     */
    @GetMapping("/chat-stats")
    public Result<Map<String, Object>> chatStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("byTier", chatLogService.statsByTier());
        stats.put("last7Days", chatLogService.statsByDay(7));
        return Result.success(stats);
    }

    /**
     * 画像准确度快速校验
     * <p>
     * 抽查部分用户画像，检查 tier 分类是否与实际数据一致
     */
    @GetMapping("/profile-check")
    public Result<Map<String, Object>> profileCheck() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 抽查几个已知层级的用户画像是否存在逻辑矛盾
        List<Map<String, String>> issues = new ArrayList<>();

        // 检查逻辑：totalSpent >= 10000 但 tier != PREMIUM
        // 这里仅做框架演示，实际可扩展为全量扫描
        try {
            // 抽样验证（取最近画像中的用户）
            Set<String> profileKeys = stringRedisTemplate.keys("user:profile:*");
            if (profileKeys != null && !profileKeys.isEmpty()) {
                int checked = 0;
                int inconsistent = 0;
                List<String> sampleKeys = new ArrayList<>(profileKeys);
                // 抽样最多 100 个
                int sampleSize = Math.min(100, sampleKeys.size());

                for (int i = 0; i < sampleSize; i++) {
                    String key = sampleKeys.get(i);
                    String userIdStr = key.replace("user:profile:", "");
                    try {
                        Long userId = Long.valueOf(userIdStr);
                        UserProfile profile = userProfileService.getProfile(userId);
                        if (profile != null && profile.getUserTier() != null) {
                            checked++;
                            // 校验：totalSpent >= 10000 则必须是 PREMIUM
                            if (profile.getTotalSpent() != null
                                    && profile.getTotalSpent().compareTo(new java.math.BigDecimal("10000")) >= 0
                                    && !"PREMIUM".equals(profile.getUserTier())) {
                                inconsistent++;
                                Map<String, String> issue = new LinkedHashMap<>();
                                issue.put("userId", userIdStr);
                                issue.put("userTier", profile.getUserTier());
                                issue.put("totalSpent", profile.getTotalSpent().toPlainString());
                                issue.put("reason", "累计消费 >= $10000 但非 PREMIUM");
                                issues.add(issue);
                            }
                        }
                    } catch (Exception ignored) {
                        // 跳过解析失败的用户
                    }
                }

                result.put("cacheProfileCount", profileKeys.size());
                result.put("sampledCount", sampleSize);
                result.put("checkedCount", checked);
                result.put("inconsistentCount", inconsistent);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        result.put("issues", issues);
        result.put("status", issues.isEmpty() ? "PASS" : "WARN");
        return Result.success(result);
    }
}
