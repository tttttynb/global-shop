package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.vo.ShipmentVo;

/**
 * 物流追踪服务
 */
public interface ShipmentService {

    /**
     * 根据订单ID查询物流信息
     * <p>
     * 买家查看自己订单的物流轨迹
     *
     * @param userId  当前用户ID（用于权限校验）
     * @param orderId 订单ID
     * @return 物流信息VO（含完整轨迹节点）
     */
    Result<ShipmentVo> getShipmentByOrderId(Long userId, Long orderId);

    /**
     * 根据运单号查询物流信息
     * <p>
     * 公开查询接口，可用于物流分享场景
     *
     * @param trackingNumber 运单号
     * @return 物流信息VO
     */
    Result<ShipmentVo> getShipmentByTrackingNumber(String trackingNumber);

    /**
     * 手动刷新物流轨迹
     * <p>
     * 对接真实物流查询API后，此方法调用第三方接口获取最新轨迹
     *
     * @param shipmentId 物流记录ID
     * @return 更新后的物流信息VO
     */
    Result<ShipmentVo> refreshTracking(Long shipmentId);
}
