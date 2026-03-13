package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVo {
    // 购物车本身的信息
    private Long cartItemId;
    private Integer quantity;

    // 从商品表里“借”过来的信息
    private Long productId;
    private String productName;
    private String coverImage;
    private BigDecimal price;

    // 后端顺手帮前端算好的：这件商品的小计金额 (单价 × 数量)
    private BigDecimal itemTotalAmount;
}
