package com.bohao.globalshop.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * 订单支付成功事件（Phase 4）
 * <p>
 * 在三个支付网关（余额/支付宝/Stripe）标记订单已支付的统一出口发布，
 * 下游监听器：F8 积分发放（下单返积分 + 成长值/等级） + F7 拼团成员支付核销。
 */
@Getter
public class OrderPaidEvent extends ApplicationEvent {

    private final Long userId;
    private final Long orderId;
    private final BigDecimal payAmount;

    public OrderPaidEvent(Object source, Long userId, Long orderId, BigDecimal payAmount) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.payAmount = payAmount;
    }
}
