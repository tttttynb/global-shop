package com.bohao.globalshop.vo;

import lombok.Data;

import java.util.List;

@Data
public class CartShopVo {
    private Long shopId;
    private String shopName;
    private List<CartItemVo> items;
}
