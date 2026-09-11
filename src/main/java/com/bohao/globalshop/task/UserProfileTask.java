package com.bohao.globalshop.task;

import com.bohao.globalshop.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户画像定时任务
 * <p>
 * 每日凌晨执行批量更新（近90天指标、偏好品类、活跃度评分）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTask {

    private final UserProfileService userProfileService;

    /**
     * 每日凌晨 2:00 执行全量画像批量更新
     * <p>
     * 更新内容：
     * - 近90天消费金额和订单数
     * - 用户层级重新判定（因为90天指标变化可能导致层级降级/升级）
     * - 偏好品类 Top3
     * - 活跃度评分
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyProfileUpdate() {
        log.info("========== 用户画像每日批量更新开始 ==========");
        long start = System.currentTimeMillis();
        try {
            userProfileService.batchUpdateRecentMetrics();
        } catch (Exception e) {
            log.error("画像批量更新异常", e);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("========== 用户画像每日批量更新完成，耗时 {} ms ==========", elapsed);
    }
}
