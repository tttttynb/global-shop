package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拼团专区活动卡片（Phase 4 - F7）
 */
@Data
public class GroupBuyActivityVo {
    private Long activityId;
    private Long productId;
    private String productName;
    private String coverImage;
    private String shopName;
    /** 日常售价（SKU价） */
    private BigDecimal originalPrice;
    /** 拼团价 */
    private BigDecimal groupPrice;
    private Integer requiredMembers;
    private Integer validHours;
    /** 当前进行中的团数量（"N 个团在拼，可直接参团"） */
    private Long ongoingGroups;
    /** 已成团件数（销量氛围） */
    private Integer soldCount;
    /** 折扣力度文案用：groupPrice / originalPrice */
    private BigDecimal discountRate;
    private LocalDateTime endTime;
}
