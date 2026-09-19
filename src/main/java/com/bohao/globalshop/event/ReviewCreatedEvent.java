package com.bohao.globalshop.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 新评价产生事件（Phase 4）
 * <p>
 * submitReview 落库后发布，下游监听器：
 * F8 评价晒单积分（限每日次数） + F9 口碑档案增量重算（防抖合并，Redis pending 集合 + 定时刷新）。
 */
@Getter
public class ReviewCreatedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long productId;
    private final Long reviewId;

    public ReviewCreatedEvent(Object source, Long userId, Long productId, Long reviewId) {
        super(source);
        this.userId = userId;
        this.productId = productId;
        this.reviewId = reviewId;
    }
}
