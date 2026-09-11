package com.bohao.globalshop.listener;

import com.bohao.globalshop.event.OrderCompletedEvent;
import com.bohao.globalshop.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用户画像事件监听器
 * <p>
 * 监听订单完成事件，异步触发画像近实时更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileEventListener {

    private final UserProfileService userProfileService;

    /**
     * 订单确认收货后，异步更新用户画像
     * <p>
     * @Async 确保不阻塞主交易流程
     */
    @Async
    @EventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("收到订单完成事件: userId={}, orderId={}, amount={}",
                event.getUserId(), event.getOrderId(), event.getOrderAmount());
        try {
            userProfileService.updateOnOrderComplete(event.getUserId(), event.getOrderAmount());
        } catch (Exception e) {
            log.error("订单完成事件处理失败: userId={}, orderId={}",
                    event.getUserId(), event.getOrderId(), e);
        }
    }
}
