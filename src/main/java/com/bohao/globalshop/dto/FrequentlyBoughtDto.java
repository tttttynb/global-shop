package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * "买了还买" 推荐 DTO（Tier 2.1b）
 * <p>
 * 基于订单共现矩阵，推荐与该商品一起购买频率最高的商品。
 * </p>
 */
@Data
public class FrequentlyBoughtDto {
    private Long id;
    private String name;
    private BigDecimal price;
    private String coverImage;
    private Integer coOccurrenceCount;
}
