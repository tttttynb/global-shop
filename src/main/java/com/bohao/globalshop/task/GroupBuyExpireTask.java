package com.bohao.globalshop.task;

import com.bohao.globalshop.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 拼团到期巡检任务（Phase 4 - F7）
 * <p>
 * 每 30 秒扫描超时未成团的团实例：置为拼团失败，
 * 未支付订单走标准取消（回补库存），已支付成员自动退款（退余额 + 回补库存 + 落退款单）。
 * 与订单超时取消（MQ 延迟消息）互补：MQ 管单笔订单，这里管"团"的生命周期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyExpireTask {

    private final GroupBuyService groupBuyService;

    @Scheduled(fixedDelayString = "${app.group-buy.expire-scan-ms:30000}")
    public void scanExpiredGroups() {
        try {
            int failed = groupBuyService.expireGroups();
            if (failed > 0) {
                log.info("⏰ 拼团到期巡检：{} 个团超时未成团，已自动处理退款/取消", failed);
            }
        } catch (Exception e) {
            log.error("❌ 拼团到期巡检任务异常", e);
        }
    }
}
