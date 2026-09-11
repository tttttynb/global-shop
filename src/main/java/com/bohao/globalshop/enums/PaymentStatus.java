package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 支付状态枚举
 * <p>
 * 追踪每一笔支付订单的完整生命周期
 */
@Getter
public enum PaymentStatus {

    /**
     * 待支付：支付订单已创建，等待用户完成支付
     */
    PENDING("待支付", 0),

    /**
     * 支付成功：用户已完成支付，资金已到账
     */
    SUCCESS("支付成功", 1),

    /**
     * 支付失败：支付被拒绝或发生错误
     */
    FAILED("支付失败", 2),

    /**
     * 已退款：支付已原路退回
     */
    REFUNDED("已退款", 3),

    /**
     * 已关闭：支付超时关闭或手动关闭
     */
    CLOSED("已关闭", 4);

    private final String label;
    private final int code;

    PaymentStatus(String label, int code) {
        this.label = label;
        this.code = code;
    }

    /**
     * 根据 code 值查找对应的枚举
     */
    public static PaymentStatus fromCode(int code) {
        for (PaymentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的支付状态编码: " + code);
    }

    /**
     * 是否已完结（成功 / 失败 / 已退款 / 已关闭 都是终态）
     */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == REFUNDED || this == CLOSED;
    }
}
