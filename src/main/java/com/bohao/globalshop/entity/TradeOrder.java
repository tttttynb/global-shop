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
    /** 🆕 订单原币币种快照（Phase 3 - F5）: CNY/USD/JPY... */
    private String currency;
    /** 🆕 下单时锁定的汇率：1 外币 = X 人民币（快照，审计对账用） */
    private BigDecimal exchangeRate;
    /** 🆕 原币金额快照 */
    private BigDecimal originalAmount;
    /** 🆕 国际运费（Phase 3 - F6） */
    private BigDecimal shippingFee;
    /** 🆕 跨境税费（Phase 3 - F6） */
    private BigDecimal taxFee;
    /** 🆕 拼团团实例ID（Phase 4 - F7），订单来源 GROUP_BUY 时非空 */
    private Long groupRecordId;
    /** 🆕 本单消耗积分（Phase 4 - F8） */
    private Integer pointsUsed;
    /** 🆕 积分抵扣金额（Phase 4 - F8） */
    private BigDecimal pointsDeduction;
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
