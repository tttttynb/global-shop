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
    /** 单规格库存；提交了 skus 时实际以 SKU 库存之和为准 */
    private Integer stock;
    private String coverImage;
    /** 多规格 SKU 列表（Phase 1 - F1），为空则按单规格处理，自动生成默认 SKU */
    private List<SkuDto> skus;
}
