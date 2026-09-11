package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 通知点击跳转的目标类型
 */
@Getter
public enum NotificationTargetType {
    ORDER("ORDER", "订单详情"),
    PRODUCT("PRODUCT", "商品详情"),
    LIVE("LIVE", "直播间"),
    COUPON("COUPON", "优惠券"),
    NONE("NONE", "无跳转");

    private final String code;
    private final String label;

    NotificationTargetType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
