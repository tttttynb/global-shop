package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class OrderCreateDto {
    private Long productId;
    /** 目标 SKU，可为空（空则后端自动落到默认 SKU，兼容旧前端） */
    private Long skuId;
    private Integer quantity;
    private Long addressId;
    private Long couponId;
    /** 🆕 拼团团实例ID（Phase 4 - F7）：非空则按拼团价下单并绑定团成员 */
    private Long groupRecordId;
    /** 🆕 是否使用积分抵扣（Phase 4 - F8） */
    private Boolean usePoints;
}
