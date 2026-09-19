package com.bohao.globalshop.listener;

import com.bohao.globalshop.event.OrderPaidEvent;
import com.bohao.globalshop.service.GroupBuyService;
import com.bohao.globalshop.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功监听器（Phase 4）
 * <p>
 * F8：下单返积分 + 成长值/等级晋升；F7：拼团成员支付核销 + 成团判定。
 * 两个下游互相独立，异常隔离，任一失败不影响另一个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {

    private final PointsService pointsService;
    private final GroupBuyService groupBuyService;

    @Async
    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) {
        // F8 会员积分：返积分 + 成长值 + 等级
        try {
            pointsService.onOrderPaid(event.getUserId(), event.getOrderId(), event.getPayAmount());
        } catch (Exception e) {
            log.error("❌ 订单返积分处理失败: orderId={}", event.getOrderId(), e);
        }
        // F7 社交拼团：成员核销 + 成团判定（非拼团单内部直接跳过）
        try {
            groupBuyService.onOrderPaid(event.getOrderId());
        } catch (Exception e) {
            log.error("❌ 拼团支付核销失败: orderId={}", event.getOrderId(), e);
        }
    }
}
