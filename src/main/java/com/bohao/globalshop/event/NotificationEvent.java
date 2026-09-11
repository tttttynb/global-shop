package com.bohao.globalshop.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 通知事件 — 业务模块发布此事件，监听器统一处理落库+推送
 * <p>
 * 使用方式：
 * <pre>
 *   &#64;Autowired
 *   private ApplicationEventPublisher eventPublisher;
 *
 *   // 订单支付成功 → 通知买家
 *   eventPublisher.publishEvent(new NotificationEvent(this,
 *       order.getUserId(),
 *       NotificationType.ORDER_STATUS.getCode(),
 *       "订单支付成功",
 *       "您的订单 #" + order.getId() + " 已支付成功，等待商家发货。",
 *       NotificationTargetType.ORDER.getCode(),
 *       order.getId()));
 * </pre>
 */
@Getter
public class NotificationEvent extends ApplicationEvent {

    /** 接收通知的用户ID */
    private final Long userId;

    /** 通知类型（对应 NotificationType.code） */
    private final String type;

    /** 通知标题 */
    private final String title;

    /** 通知正文 */
    private final String content;

    /** 点击跳转类型（对应 NotificationTargetType.code），可为 null */
    private final String targetType;

    /** 跳转目标ID，可为 null */
    private final Long targetId;

    public NotificationEvent(Object source,
                             Long userId,
                             String type,
                             String title,
                             String content,
                             String targetType,
                             Long targetId) {
        super(source);
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
