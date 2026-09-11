package com.bohao.globalshop.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流信息 VO
 * <p>
 * 包含物流基本信息 + 完整轨迹节点列表，
 * 供前端物流追踪时间线组件使用
 */
@Data
public class ShipmentVo {

    private Long id;
    private Long orderId;
    private String carrierName;
    private String carrierCode;
    private String trackingNumber;
    private Integer status;
    private String statusLabel;
    private String currentLocation;
    private LocalDate estimatedDelivery;
    private LocalDateTime deliveredTime;

    /** 物流轨迹节点列表 */
    private List<TrackingNode> trackingNodes;

    private LocalDateTime createTime;

    /**
     * 物流轨迹节点
     * <p>
     * 每个节点代表物流过程中的一个关键事件
     */
    @Data
    public static class TrackingNode {
        /** 事件时间 */
        private String time;
        /** 事件状态描述 */
        private String status;
        /** 事件发生地点 */
        private String location;
        /** 事件详细描述 */
        private String description;
    }
}
