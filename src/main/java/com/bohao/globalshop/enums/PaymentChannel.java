package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 支付渠道枚举
 * <p>
 * 用于支付网关路由和订单支付记录
 */
@Getter
public enum PaymentChannel {

    /**
     * 余额支付（平台内置钱包）
     */
    BALANCE("余额支付", 0),

    /**
     * 支付宝（中国 + 跨境）
     */
    ALIPAY("支付宝", 1),

    /**
     * 微信支付
     */
    WECHAT("微信支付", 2),

    /**
     * Stripe（国际信用卡支付）
     */
    STRIPE("Stripe", 3);

    private final String label;
    private final int code;

    PaymentChannel(String label, int code) {
        this.label = label;
        this.code = code;
    }

    /**
     * 根据 code 值查找对应的枚举
     */
    public static PaymentChannel fromCode(int code) {
        for (PaymentChannel channel : values()) {
            if (channel.code == code) {
                return channel;
            }
        }
        throw new IllegalArgumentException("未知的支付渠道编码: " + code);
    }

    /**
     * 根据渠道名称查找对应的枚举（回调时使用）
     */
    public static PaymentChannel fromName(String name) {
        for (PaymentChannel channel : values()) {
            if (channel.name().equalsIgnoreCase(name)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("未知的支付渠道名称: " + name);
    }
}
