package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class ReviewSubmitDto {
    // 凭证：你要评价的是你买的哪一件商品？（对应 trade_order_item 表的 ID）
    private Long orderItemId;
    private Integer rating;
    private String content;
    private String images;
}
