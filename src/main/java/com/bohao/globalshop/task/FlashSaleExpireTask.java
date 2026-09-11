package com.bohao.globalshop.task;

import com.bohao.globalshop.service.LiveFlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀活动过期收尾任务（Phase 2 - F3）
 * <p>
 * 每 10 秒扫描一次：把已过 end_time 但仍 status=0 的活动置为已结束，
 * 剩余库存回补 SKU，并通过直播间 WebSocket 广播 END 事件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleExpireTask {

    private final LiveFlashSaleService flashSaleService;

    @Scheduled(fixedDelay = 10_000)
    public void scanExpiredFlashSales() {
        try {
            flashSaleService.finishExpired();
        } catch (Exception e) {
            log.error("❌ 秒杀过期扫描任务异常", e);
        }
    }
}
