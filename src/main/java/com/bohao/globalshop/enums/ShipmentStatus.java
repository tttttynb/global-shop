package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 物流状态枚举
 * <p>
 * 追踪包裹从揽收到签收的完整生命周期
 */
@Getter
public enum ShipmentStatus {

    /**
     * 待揽收：运单已创建，等待快递员取件
     */
    PENDING_PICKUP("待揽收", 0),

    /**
     * 运输中：包裹已揽收，正在运输途中
     */
    IN_TRANSIT("运输中", 1),

    /**
     * 派送中：包裹已到达目的地城市，快递员正在派送
     */
    DELIVERING("派送中", 2),

    /**
     * 已签收：买家已签收包裹
     */
    DELIVERED("已签收", 3),

    /**
     * 异常：物流出现问题（地址不详、拒收、退回等）
     */
    ABNORMAL("异常", 4);

    private final String label;
    private final int code;

    ShipmentStatus(String label, int code) {
        this.label = label;
        this.code = code;
    }

    public static ShipmentStatus fromCode(int code) {
        for (ShipmentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的物流状态编码: " + code);
    }
}
