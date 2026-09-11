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
}
