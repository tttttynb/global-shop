package com.bohao.globalshop.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * 订单完成事件 — 用户确认收货时发布
 * <p>
 * 用于触发用户画像近实时更新
 */
@Getter
public class OrderCompletedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long orderId;
    private final BigDecimal orderAmount;

    public OrderCompletedEvent(Object source, Long userId, Long orderId, BigDecimal orderAmount) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.orderAmount = orderAmount;
    }
}
