package com.bohao.globalshop.task;

import com.bohao.globalshop.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 汇率刷新定时任务（Phase 3 - F5 多币种）
 * <p>
 * 每小时多源拉取免费汇率 API（er-api → frankfurter 兜底）→ 落库 → Redis 缓存 1 小时。
 * 应用启动后立即预热一次，保证首个用户请求就能拿到实时汇率。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateTask {

    private final ExchangeRateService exchangeRateService;

    /**
     * 每小时整点刷新（cron 可配置：app.forex.refresh-cron）
     */
    @Scheduled(cron = "${app.forex.refresh-cron:0 0 * * * ?}")
    public void refreshHourly() {
        log.info("⏰ 定时汇率刷新开始...");
        try {
            int updated = exchangeRateService.refreshRates();
            log.info("⏰ 定时汇率刷新完成，更新 {} 个币种", updated);
        } catch (Exception e) {
            log.error("❌ 定时汇率刷新异常（旧汇率继续可用）", e);
        }
    }

    /**
     * 启动预热：应用就绪后立即拉一次，失败不影响启动（库内有迁移脚本预置的兜底汇率）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        try {
            int updated = exchangeRateService.refreshRates();
            if (updated > 0) {
                log.info("🚀 启动汇率预热完成，更新 {} 个币种", updated);
            }
        } catch (Exception e) {
            log.warn("启动汇率预热失败（使用库内汇率）: {}", e.getMessage());
        }
    }
}
