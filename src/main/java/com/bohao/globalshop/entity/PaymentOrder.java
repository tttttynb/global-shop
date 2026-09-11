package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体
 * <p>
 * 记录每一笔支付交易的完整生命周期，支持多渠道支付追踪和对账
 */
@Data
@TableName("payment_order")
public class PaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的订单ID (trade_order.id) */
    private Long orderId;

    /** 支付用户ID */
    private Long userId;

    /** 支付渠道编码: 0-余额 1-支付宝 2-微信 3-Stripe */
    private Integer channel;

    /** 支付渠道名称: "余额支付"/"支付宝"/"微信支付"/"Stripe" */
    private String channelName;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付状态: 0-待支付 1-成功 2-失败 3-已退款 4-已关闭 */
    private Integer status;

    /** 网关返回的交易流水号 */
    private String transactionId;

    /** 商户订单号 (唯一，格式: GS + 时间戳 + 随机数) */
    private String outTradeNo;

    /** 回调原始数据 (JSON，用于对账排查) */
    private String callbackData;

    /** 回调时间 */
    private LocalDateTime callbackTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
