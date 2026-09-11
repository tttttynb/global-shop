package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 消息通知类型
 */
@Getter
public enum NotificationType {
    ORDER_STATUS("ORDER_STATUS", "订单状态"),
    COUPON_EXPIRE("COUPON_EXPIRE", "优惠券到期"),
    LIVE_START("LIVE_START", "直播开播"),
    PROMOTION("PROMOTION", "促销活动"),
    SYSTEM("SYSTEM", "系统通知");

    private final String code;
    private final String label;

    NotificationType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return SYSTEM;
    }
}
