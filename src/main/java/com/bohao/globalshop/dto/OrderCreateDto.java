package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class OrderCreateDto {
    private Long productId;
    private Integer quantity;
    private Long addressId;
    private Long couponId;
}
