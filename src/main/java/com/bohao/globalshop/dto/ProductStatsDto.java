package com.bohao.globalshop.dto;

import lombok.Data;

/**
 * 商品实时统计 DTO（Tier 1.3 — 社交证明）
 */
@Data
public class ProductStatsDto {

    /** 当前正在浏览的用户数 */
    private int viewersCount;

    /** 今日下单数 */
    private int todayOrderCount;

    /** 本周销量 */
    private int weeklySalesCount;

    /** 累计销量 */
    private int totalSalesCount;
}
