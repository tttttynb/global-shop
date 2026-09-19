package com.bohao.globalshop.task;

import com.bohao.globalshop.service.ReviewIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI口碑档案防抖刷新任务（Phase 4 - F9）
 * <p>
 * 新评价只把商品 ID 记入 Redis pending 集合（markPending），
 * 本任务每 5 分钟批量弹出并合并重算 —— 同一商品短时间多条评价只触发一次 LLM 调用（防抖合并），
 * 重算后异步翻译多语言版本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewIntelligenceTask {

    private final ReviewIntelligenceService reviewIntelligenceService;

    @Scheduled(fixedDelayString = "${app.reputation.flush-interval-ms:300000}")
    public void flushPendingProducts() {
        try {
            reviewIntelligenceService.flushPending();
        } catch (Exception e) {
            log.error("❌ 口碑档案防抖刷新任务异常", e);
        }
    }
}
