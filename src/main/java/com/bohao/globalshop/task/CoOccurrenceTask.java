package com.bohao.globalshop.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.entity.TradeOrderItem;
import com.bohao.globalshop.mapper.TradeOrderItemMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品共现矩阵定时任务（Tier 2.1b）
 * <p>
 * 每天凌晨 3:15 执行，分析近 90 天已完成订单中的商品共现关系，
 * 将 top 10 关联商品写入 Redis ZSet：frequently:bought:{productId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoOccurrenceTask {

    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TraderOrderMapper tradeOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final int TOP_N = 10;
    private static final int BATCH_SIZE = 100;

    /**
     * 每天凌晨 3:15 执行（画像凌晨2:00更新完之后）
     */
    @Scheduled(cron = "0 15 3 * * ?")
    public void buildCoOccurrenceMatrix() {
        log.info("========== 商品共现矩阵构建开始 ==========");
        long start = System.currentTimeMillis();

        try {
            // 1. 查询近 90 天已完成订单
            QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
            orderQw.in("status", Arrays.asList(4, 5)); // 已收货、已评价
            orderQw.ge("create_time", java.time.LocalDateTime.now().minusDays(90));
            List<TradeOrder> recentOrders = tradeOrderMapper.selectList(orderQw);

            if (recentOrders.isEmpty()) {
                log.info("近 90 天无已完成订单，跳过共现计算");
                return;
            }

            List<Long> orderIds = recentOrders.stream()
                    .map(TradeOrder::getId)
                    .collect(Collectors.toList());

            // 2. 查询这些订单的所有商品明细
            QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
            itemQw.in("order_id", orderIds);
            List<TradeOrderItem> allItems = tradeOrderItemMapper.selectList(itemQw);

            // 3. 按 orderId 分组
            Map<Long, List<TradeOrderItem>> orderGrouped = allItems.stream()
                    .collect(Collectors.groupingBy(TradeOrderItem::getOrderId));

            // 4. 计算共现对（双向计数）
            // 用本地 Map 聚合，再批量写入 Redis，减少 Redis 网络往返
            Map<String, Map<String, Integer>> batchCounts = new HashMap<>();
            int totalPairs = 0;
            int batchCount = 0;

            for (Map.Entry<Long, List<TradeOrderItem>> entry : orderGrouped.entrySet()) {
                List<TradeOrderItem> items = entry.getValue();
                if (items.size() < 2) continue; // 单商品订单无共现关系

                for (int i = 0; i < items.size(); i++) {
                    for (int j = i + 1; j < items.size(); j++) {
                        Long prodA = items.get(i).getProductId();
                        Long prodB = items.get(j).getProductId();
                        if (prodA == null || prodB == null || prodA.equals(prodB)) continue;

                        // 双向计数
                        incrementPair(batchCounts, String.valueOf(prodA), String.valueOf(prodB));
                        incrementPair(batchCounts, String.valueOf(prodB), String.valueOf(prodA));
                        totalPairs += 2;
                    }
                }

                batchCount++;
                // 每 BATCH_SIZE 个订单批量写一次 Redis
                if (batchCount >= BATCH_SIZE) {
                    flushToRedis(batchCounts);
                    batchCounts.clear();
                    batchCount = 0;
                }
            }
            // 刷剩余数据
            flushToRedis(batchCounts);

            log.info("共现计算完成：{} 个订单，{} 个商品对", orderGrouped.size(), totalPairs);

            // 5. 为每个商品取 top N，写入 ZSet
            buildTopNZSets();

            log.info("共现矩阵 top {} 写入 Redis 完成", TOP_N);
        } catch (Exception e) {
            log.error("共现矩阵构建失败", e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("========== 商品共现矩阵构建完成，耗时 {} ms ==========", elapsed);
    }

    private void incrementPair(Map<String, Map<String, Integer>> batch, String prodA, String prodB) {
        batch.computeIfAbsent(prodA, k -> new HashMap<>())
                .merge(prodB, 1, Integer::sum);
    }

    private void flushToRedis(Map<String, Map<String, Integer>> batch) {
        if (batch.isEmpty()) return;
        for (Map.Entry<String, Map<String, Integer>> entry : batch.entrySet()) {
            String prodKey = entry.getKey();
            Map<String, Integer> pairs = entry.getValue();
            for (Map.Entry<String, Integer> pair : pairs.entrySet()) {
                stringRedisTemplate.opsForHash()
                        .increment("cooccur:matrix:" + prodKey, pair.getKey(), pair.getValue());
            }
        }
    }

    /**
     * 从 Hash 中取每个商品的 top N 共现商品，写入 ZSet
     */
    private void buildTopNZSets() {
        // 扫描所有 cooccur:matrix:* 的 key
        Set<String> keys = stringRedisTemplate.keys("cooccur:matrix:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String productId = key.substring("cooccur:matrix:".length());

            // 获取该商品的所有共现对及其计数
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) continue;

            // 按计数降序排序，取 top N
            List<Map.Entry<Object, Object>> sorted = entries.entrySet().stream()
                    .sorted((a, b) -> Long.compare(
                            Long.parseLong(String.valueOf(b.getValue())),
                            Long.parseLong(String.valueOf(a.getValue()))))
                    .limit(TOP_N)
                    .collect(Collectors.toList());

            String zsetKey = "frequently:bought:" + productId;
            for (Map.Entry<Object, Object> e : sorted) {
                stringRedisTemplate.opsForZSet().add(zsetKey,
                        String.valueOf(e.getKey()),
                        Double.parseDouble(String.valueOf(e.getValue())));
            }
            // 只保留 top N
            stringRedisTemplate.opsForZSet()
                    .removeRange(zsetKey, TOP_N, -1);

            // 设置 7 天过期，避免僵尸数据
            stringRedisTemplate.expire(zsetKey, java.time.Duration.ofDays(7));
        }
    }
}
