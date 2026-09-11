package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物流信息实体
 * <p>
 * 记录每笔订单的发货物流信息，包括物流公司、运单号、
 * 物流状态和完整轨迹数据
 */
@Data
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单ID (FK → trade_order.id)，一对一关系 */
    private Long orderId;

    /** 物流公司名称：顺丰速运 / 中通快递 / DHL / FedEx */
    private String carrierName;

    /** 物流公司编码：SF / ZTO / DHL / FEDEX（对接第三方API时使用） */
    private String carrierCode;

    /** 运单号 */
    private String trackingNumber;

    /** 物流状态: 0-待揽收 1-运输中 2-派送中 3-已签收 4-异常 */
    private Integer status;

    /** 当前位置描述 */
    private String currentLocation;

    /** 预计送达日期 */
    private LocalDate estimatedDelivery;

    /** 签收时间 */
    private LocalDateTime deliveredTime;

    /** 完整物流轨迹（JSON数组字符串） */
    private String trackingData;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
