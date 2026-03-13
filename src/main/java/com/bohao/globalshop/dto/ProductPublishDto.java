package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductPublishDto {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String coverImage;
}
