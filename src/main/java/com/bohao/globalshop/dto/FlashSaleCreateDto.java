package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 主播发起秒杀活动
 */
@Data
public class FlashSaleCreateDto {
    private Long roomId;
    private Long productId;
    /** 目标 SKU，可为空（默认 SKU） */
    private Long skuId;
    private BigDecimal flashPrice;
    private Integer totalQty;
    /** 活动时长（分钟），默认 3 分钟 */
    private Integer durationMinutes;
}
