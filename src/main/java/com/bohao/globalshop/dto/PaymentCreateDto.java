package com.bohao.globalshop.dto;

import lombok.Data;

/**
 * 创建支付请求 DTO
 */
@Data
public class PaymentCreateDto {

    /** 订单ID */
    private Long orderId;

    /** 支付渠道编码: 0-余额 1-支付宝 2-微信 3-Stripe */
    private Integer channel;
}
