package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductPublishDto {
    private String name;
    private String description;
    /** 单规格价格；提交了 skus 时作为兜底，实际以 SKU 聚合价（最低价）为准 */
    private BigDecimal price;
    /** 🆕 原币币种代码（Phase 3 - F5）: CNY/USD/JPY/EUR/KRW/THB/GBP，空默认 CNY */
    private String originalCurrency;
    /** 🆕 原币定价（如日本商品 3980 日元）；price 为空时后端按当前汇率自动折算人民币价 */
    private BigDecimal originalPrice;
    /** 🆕 发货国家/地区，如 日本、美国 */
    private String originCountry;
    /** 单规格库存；提交了 skus 时实际以 SKU 库存之和为准 */
    private Integer stock;
    private String coverImage;
    /** 多规格 SKU 列表（Phase 1 - F1），为空则按单规格处理，自动生成默认 SKU */
    private List<SkuDto> skus;
}
