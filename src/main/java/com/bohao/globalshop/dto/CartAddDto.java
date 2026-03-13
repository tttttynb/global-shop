package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class CartAddDto {
    private Long productId;
    private Integer quantity;
}

