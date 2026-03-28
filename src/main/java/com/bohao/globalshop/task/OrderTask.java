package com.bohao.globalshop.task;

import com.bohao.globalshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTask {
    public final StringRedisTemplate stringRedisTemplate;
    public final OrderService orderService;

    @Scheduled(cron = "0/5 * * * * ?")
    public void processDelayedOrders() {
        long now = System.currentTimeMillis();
        // 核心微操：从 ZSet 里拿出所有 Score (过期时间) 小于等于当前时间的订单号
        Set<String> timeoutOrderId = stringRedisTemplate.opsForZSet().rangeByScore("order:timeout:queue", 0, now);
        if (timeoutOrderId != null && !timeoutOrderId.isEmpty()) {
            for (String orderIdStr : timeoutOrderId) {
                Long orderId = Long.valueOf(orderIdStr);
                // 防并发双杀：先尝试从 Redis 里删掉这个 ID，删成功了才去执行取消逻辑！
                // 这在分布式系统里相当于一个极其轻量的“分布式锁”
                Long removed = stringRedisTemplate.opsForZSet().remove("order:timeout:queue", orderIdStr);
                if (removed != null && removed > 0) {
                    try {
                        // 让Service 去干活：改状态、退库存！
                        orderService.cancelSingleOrder(orderId);
                        log.info("🚨 Redis 延迟队列触发：订单 [{}] 已超时取消，库存完美回退！", orderId);

                    } catch (Exception e) {
                        log.error("订单取消失败，可能需要人工介入: {}", orderId, e);
                    }
                }
            }
        }
    }
}
