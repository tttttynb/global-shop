package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    /** 人民币成交价（多币种商品 = 原币价 × 发布时点汇率，SKU 价以此为准） */
    private BigDecimal price;
    /** 🆕 原币币种代码（Phase 3 - F5）: CNY/USD/JPY/EUR/KRW/THB/GBP */
    private String originalCurrency;
    /** 🆕 原币定价（如日本商品 3980 日元），CNY 商品为空 */
    private BigDecimal originalPrice;
    /** 🆕 发货国家/地区，如 日本、美国 */
    private String originCountry;
    private Integer stock;
    private String coverImage;
    private Long categoryId;
    private Integer status;
    private Integer salesCount;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
