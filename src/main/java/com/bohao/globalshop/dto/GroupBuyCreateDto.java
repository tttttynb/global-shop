package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家创建拼团活动（Phase 4 - F7）
 */
@Data
public class GroupBuyCreateDto {
    private Long productId;
    /** 可空 = 默认SKU */
    private Long skuId;
    /** 拼团价 */
    private BigDecimal groupPrice;
    /** 成团人数（2-10） */
    private Integer requiredMembers;
    /** 开团后有效时长（小时） */
    private Integer validHours;
    /** 每人限参团次数，默认1 */
    private Integer perUserLimit;
}
