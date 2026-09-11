package com.bohao.globalshop.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.Shipment;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.mapper.ShipmentMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动确认收货定时任务
 * <p>
 * 规则：
 * 1. 物流已签收超过7天的订单 → 自动确认收货
 * 2. 已发货超过14天的订单（即使物流未显示签收）→ 自动确认收货
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmReceiptTask {

    private final ShipmentMapper shipmentMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final OrderService orderService;

    /**
     * 每天凌晨2点执行自动确认收货
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoConfirm() {
        log.info("========== 自动确认收货任务开始 ==========");
        int confirmedCount = 0;

        try {
            // 规则1：物流已签收超过7天的订单
            QueryWrapper<Shipment> deliveredQw = new QueryWrapper<>();
            deliveredQw.eq("status", 3)
                    .le("delivered_time", LocalDateTime.now().minusDays(7));
            List<Shipment> deliveredShipments = shipmentMapper.selectList(deliveredQw);

            for (Shipment s : deliveredShipments) {
                try {
                    autoConfirmOrder(s.getId(), s.getOrderId(), "物流已签收超过7天");
                    confirmedCount++;
                } catch (Exception e) {
                    log.error("自动确认收货失败: shipmentId={}, orderId={}", s.getId(), s.getOrderId(), e);
                }
            }

            // 规则2：已发货超过14天但物流未签收的订单
            QueryWrapper<TradeOrder> shippedQw = new QueryWrapper<>();
            shippedQw.eq("status", 3)
                    .le("shipped_at", LocalDateTime.now().minusDays(14));
            List<TradeOrder> longShippedOrders = traderOrderMapper.selectList(shippedQw);

            for (TradeOrder order : longShippedOrders) {
                try {
                    autoConfirmOrder(null, order.getId(), "已发货超过14天");
                    confirmedCount++;
                } catch (Exception e) {
                    log.error("自动确认收货失败: orderId={}", order.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("自动确认收货任务异常", e);
        }

        log.info("========== 自动确认收货任务结束，共确认 {} 笔订单 ==========", confirmedCount);
    }

    private void autoConfirmOrder(Long shipmentId, Long orderId, String reason) {
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 3) {
            return; // 已不是"已发货"状态，跳过
        }
        // 调用确认收货（无需userId，直接用系统操作）
        order.setStatus(4);
        order.setUpdateTime(LocalDateTime.now());
        traderOrderMapper.updateById(order);

        // 更新物流状态为已签收（如果还没签收）
        if (shipmentId != null) {
            Shipment shipment = shipmentMapper.selectById(shipmentId);
            if (shipment != null && shipment.getStatus() != 3) {
                shipment.setStatus(3);
                shipment.setDeliveredTime(LocalDateTime.now());
                shipmentMapper.updateById(shipment);
            }
        }

        log.info("自动确认收货: orderId={}, reason={}", orderId, reason);
    }
}
