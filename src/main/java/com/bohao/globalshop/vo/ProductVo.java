package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVo {
    private Long id;
    private Long shopId;
    // 专门用来给前端展示的店铺名！
    private String shopName;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String coverImage;
}
