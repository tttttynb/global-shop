package com.bohao.globalshop.task;

import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 降价/补货提醒定时任务（Tier 2.2）
 * <p>
 * 每分钟扫描订阅列表，检测价格变动和库存恢复，生成通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductMapper productMapper;

    /**
     * 每分钟检查降价
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkPriceDrops() {
        try {
            Set<String> keys = stringRedisTemplate.keys("alert:price:*");
            if (keys == null) return;

            for (String key : keys) {
                // 跳过 last 价格记录 key
                if (key.startsWith("alert:price:last:")) continue;

                String productIdStr = key.substring("alert:price:".length());
                Long productId;
                try {
                    productId = Long.valueOf(productIdStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                // 查当前价格
                Product product = productMapper.selectById(productId);
                if (product == null || product.getPrice() == null) continue;

                BigDecimal currentPrice = product.getPrice();

                // 查上次记录的价格
                String lastPriceStr = stringRedisTemplate.opsForValue()
                        .get("alert:price:last:" + productId);
                BigDecimal lastPrice = null;
                if (lastPriceStr != null) {
                    try {
                        lastPrice = new BigDecimal(lastPriceStr);
                    } catch (NumberFormatException ignored) {}
                }

                // 初始化上次价格（首次运行）
                if (lastPrice == null) {
                    stringRedisTemplate.opsForValue()
                            .set("alert:price:last:" + productId, currentPrice.toString());
                    continue;
                }

                // 检测降价
                if (currentPrice.compareTo(lastPrice) < 0) {
                    // 通知所有订阅用户
                    Set<String> userIds = stringRedisTemplate.opsForSet().members(key);
                    if (userIds != null && !userIds.isEmpty()) {
                        String message = "降价:" + productId + ":"
                                + product.getName()
                                + " ¥" + lastPrice + "→¥" + currentPrice;
                        long now = System.currentTimeMillis();
                        for (String uid : userIds) {
                            stringRedisTemplate.opsForZSet()
                                    .add("alert:notif:" + uid, message, now);
                        }
                        log.info("降价通知：商品 {} ({}), {} 位用户收到通知",
                                productId, product.getName(), userIds.size());
                    }
                }

                // 更新上次价格
                stringRedisTemplate.opsForValue()
                        .set("alert:price:last:" + productId, currentPrice.toString());
            }
        } catch (Exception e) {
            log.error("降价检查任务异常", e);
        }
    }

    /**
     * 每分钟检查补货
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkRestock() {
        try {
            Set<String> keys = stringRedisTemplate.keys("alert:restock:*");
            if (keys == null) return;

            for (String key : keys) {
                String productIdStr = key.substring("alert:restock:".length());
                Long productId;
                try {
                    productId = Long.valueOf(productIdStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                Product product = productMapper.selectById(productId);
                if (product == null) continue;

                // 检测补货：之前缺货，现在有库存了
                if (product.getStock() != null && product.getStock() > 0) {
                    Set<String> userIds = stringRedisTemplate.opsForSet().members(key);
                    if (userIds != null && !userIds.isEmpty()) {
                        String message = "补货:" + productId + ":"
                                + product.getName()
                                + " 已到货（库存 " + product.getStock() + " 件）";
                        long now = System.currentTimeMillis();
                        for (String uid : userIds) {
                            stringRedisTemplate.opsForZSet()
                                    .add("alert:notif:" + uid, message, now);
                        }
                        log.info("补货通知：商品 {} ({}), {} 位用户收到通知",
                                productId, product.getName(), userIds.size());
                    }
                    // 清除订阅（补货后不再监控）
                    stringRedisTemplate.delete(key);
                }
            }
        } catch (Exception e) {
            log.error("补货检查任务异常", e);
        }
    }
}
