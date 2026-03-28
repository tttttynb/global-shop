package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVo {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String coverImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal itemTotalAmount;
}
