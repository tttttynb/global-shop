package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付结果 VO
 * <p>
 * 前端根据 channel 不同，使用不同的字段展示支付界面：
 * - 余额支付：直接扣款成功，无需额外展示
 * - 支付宝/微信：展示 payUrl 或 qrCode
 * - Stripe：展示 clientSecret（前端用 Stripe.js 完成支付）
 */
@Data
public class PaymentResultVo {

    /** 支付订单ID */
    private Long paymentId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付渠道编码 */
    private Integer channel;

    /** 支付渠道名称 */
    private String channelName;

    /** 支付页面URL（支付宝/微信扫码支付时使用） */
    private String payUrl;

    /** 二维码链接（扫码支付时使用） */
    private String qrCode;

    /** Stripe PaymentIntent clientSecret（Stripe.js 使用） */
    private String clientSecret;

    /** 状态码（余额支付时直接返回成功状态） */
    private Integer status;

    /** 状态描述 */
    private String statusLabel;
}
