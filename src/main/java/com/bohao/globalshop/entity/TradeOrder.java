package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    // 移除 productId 和 quantity，由 TradeOrderItem 承担
    private BigDecimal totalAmount;
    private Integer status;
    /** 订单来源: NORMAL=普通 / LIVE_FLASH=直播秒杀（Phase 2 - F3） */
    private String orderSource;
    /** 关联的秒杀活动ID */
    private Long flashSaleId;
    private Long couponId;
    private BigDecimal discountAmount;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    /** 支付渠道名称（余额支付/支付宝/微信支付/Stripe） */
    private String paymentType;
    /** 关联的支付订单ID (payment_order.id) */
    private Long paymentId;
    /** 支付完成时间 */
    private LocalDateTime payTime;
    /** 物流公司名称 */
    private String carrierName;
    /** 运单号 */
    private String trackingNumber;
    /** 发货时间 */
    private LocalDateTime shippedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
