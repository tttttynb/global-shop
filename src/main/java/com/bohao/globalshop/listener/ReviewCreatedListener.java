package com.bohao.globalshop.listener;

import com.bohao.globalshop.event.ReviewCreatedEvent;
import com.bohao.globalshop.service.PointsService;
import com.bohao.globalshop.service.ReviewIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 新评价事件监听器（Phase 4）
 * <p>
 * F8：评价晒单返积分（Redis 日计数限次防刷）；
 * F9：口碑档案增量重算 —— 只标记 Redis pending 集合（防抖合并），由定时任务批量刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCreatedListener {

    private final PointsService pointsService;
    private final ReviewIntelligenceService reviewIntelligenceService;

    @Async
    @EventListener
    public void handleReviewCreated(ReviewCreatedEvent event) {
        try {
            pointsService.onReviewCreated(event.getUserId(), event.getReviewId());
        } catch (Exception e) {
            log.error("❌ 评价返积分失败: reviewId={}", event.getReviewId(), e);
        }
        try {
            reviewIntelligenceService.markPending(event.getProductId());
        } catch (Exception e) {
            log.error("❌ 口碑档案重算标记失败: productId={}", event.getProductId(), e);
        }
    }
}
