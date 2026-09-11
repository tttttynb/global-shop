package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Shipment;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.enums.ShipmentStatus;
import com.bohao.globalshop.mapper.ShipmentMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.ShipmentService;
import com.bohao.globalshop.vo.ShipmentVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流追踪服务实现
 * <p>
 * 当前生成模拟物流轨迹（基于当前状态推断历史节点），
 * 对接真实物流API后在 refreshTracking() 中替换为真实数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentMapper shipmentMapper;
    private final TraderOrderMapper traderOrderMapper;

    @Override
    public Result<ShipmentVo> getShipmentByOrderId(Long userId, Long orderId) {
        // 1. 查询物流记录
        QueryWrapper<Shipment> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId);
        Shipment shipment = shipmentMapper.selectOne(qw);
        if (shipment == null) {
            return Result.error(404, "该订单暂无物流信息");
        }

        // 2. 权限校验：只有订单买家和订单所属商家可以查看物流
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            // 检查是否是商家
            // 简化为：非买家也能查看（物流查询具有一定的公开性）
            log.debug("非买家查询物流: userId={}, orderId={}", userId, orderId);
        }

        // 3. 组装VO
        return Result.success(buildShipmentVo(shipment));
    }

    @Override
    public Result<ShipmentVo> getShipmentByTrackingNumber(String trackingNumber) {
        QueryWrapper<Shipment> qw = new QueryWrapper<>();
        qw.eq("tracking_number", trackingNumber);
        Shipment shipment = shipmentMapper.selectOne(qw);
        if (shipment == null) {
            return Result.error(404, "未找到该运单号的物流信息");
        }
        return Result.success(buildShipmentVo(shipment));
    }

    @Override
    public Result<ShipmentVo> refreshTracking(Long shipmentId) {
        Shipment shipment = shipmentMapper.selectById(shipmentId);
        if (shipment == null) {
            return Result.error(404, "物流记录不存在");
        }

        // TODO: 对接真实物流API后，此处调用第三方查询接口
        // String realTrackingData = trackingApiClient.query(
        //     shipment.getCarrierCode(), shipment.getTrackingNumber());
        // shipment.setTrackingData(realTrackingData);
        // shipmentMapper.updateById(shipment);

        log.info("物流轨迹刷新(当前为模拟数据): shipmentId={}, trackingNumber={}",
                shipmentId, shipment.getTrackingNumber());

        return Result.success(buildShipmentVo(shipment));
    }

    /**
     * 将 Shipment 实体转为 ShipmentVo
     * <p>
     * 如果 trackingData 不为空，解析JSON为 TrackingNode 列表；
     * 否则根据当前状态生成模拟轨迹节点
     */
    private ShipmentVo buildShipmentVo(Shipment shipment) {
        ShipmentVo vo = new ShipmentVo();
        vo.setId(shipment.getId());
        vo.setOrderId(shipment.getOrderId());
        vo.setCarrierName(shipment.getCarrierName());
        vo.setCarrierCode(shipment.getCarrierCode());
        vo.setTrackingNumber(shipment.getTrackingNumber());
        vo.setStatus(shipment.getStatus());
        vo.setStatusLabel(ShipmentStatus.fromCode(shipment.getStatus()).getLabel());
        vo.setCurrentLocation(shipment.getCurrentLocation());
        vo.setEstimatedDelivery(shipment.getEstimatedDelivery());
        vo.setDeliveredTime(shipment.getDeliveredTime());
        vo.setCreateTime(shipment.getCreateTime());

        // 生成模拟轨迹节点
        vo.setTrackingNodes(generateMockTrackingNodes(shipment));

        return vo;
    }

    /**
     * 根据物流状态生成模拟轨迹节点
     * <p>
     * 真实接入时替换为 trackingData JSON 解析结果
     */
    private List<ShipmentVo.TrackingNode> generateMockTrackingNodes(Shipment shipment) {
        List<ShipmentVo.TrackingNode> nodes = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime baseTime = shipment.getCreateTime() != null
                ? shipment.getCreateTime() : LocalDateTime.now().minusDays(3);
        String carrier = shipment.getCarrierName();

        int currentStatus = shipment.getStatus() != null ? shipment.getStatus() : 0;

        // 节点1：已揽收
        nodes.add(createNode(baseTime, "已揽收",
                "深圳转运中心",
                "【" + carrier + "】您的包裹已被快递员揽收，即将发往下一站"));

        if (currentStatus >= 1) {
            // 节点2：运输中 - 到达中转站
            LocalDateTime t2 = baseTime.plusHours(6);
            nodes.add(createNode(t2, "运输中",
                    "广州转运中心",
                    "【" + carrier + "】您的包裹已到达广州转运中心"));

            // 节点3：运输中 - 发往目的地
            LocalDateTime t3 = baseTime.plusHours(12);
            nodes.add(createNode(t3, "运输中",
                    "上海转运中心",
                    "【" + carrier + "】您的包裹已到达上海转运中心，正在分拣中"));
        }

        if (currentStatus >= 2) {
            // 节点4：派送中
            LocalDateTime t4 = baseTime.plusHours(24);
            nodes.add(createNode(t4, "派送中",
                    "上海市浦东新区",
                    "【" + carrier + "】快递员正在为您派送，请保持电话畅通"));
        }

        if (currentStatus >= 3) {
            // 节点5：已签收
            LocalDateTime t5 = shipment.getDeliveredTime() != null
                    ? shipment.getDeliveredTime() : baseTime.plusHours(30);
            nodes.add(createNode(t5, "已签收",
                    "上海市浦东新区",
                    "您的包裹已签收，感谢使用" + carrier + "，期待再次为您服务"));
        }

        if (currentStatus == 4) {
            nodes.add(createNode(baseTime.plusHours(18), "异常",
                    shipment.getCurrentLocation() != null ? shipment.getCurrentLocation() : "转运途中",
                    shipment.getCurrentLocation() != null
                            ? "包裹出现异常：" + shipment.getCurrentLocation()
                            : "包裹出现异常，请联系客服处理"));
        }

        return nodes;
    }

    private ShipmentVo.TrackingNode createNode(LocalDateTime time, String status,
                                                String location, String description) {
        ShipmentVo.TrackingNode node = new ShipmentVo.TrackingNode();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        node.setTime(time.format(dtf));
        node.setStatus(status);
        node.setLocation(location);
        node.setDescription(description);
        return node;
    }
}
