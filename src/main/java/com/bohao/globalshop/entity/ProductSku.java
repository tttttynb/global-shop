package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU（Phase 1 - F1 规格系统）
 * <p>
 * 每个 SKU 独立价格/库存/图片。存量单规格商品会自动生成 isDefault=1 的默认 SKU。
 * 库存扣减不使用 @Version 乐观锁，而是走 SkuService 的原子 SQL（stock = stock - N WHERE stock >= N）
 * + Redis Lua 预扣减双层防超卖。
 * </p>
 */
@Data
@TableName("product_sku")
public class ProductSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    /** 规格 JSON，如 {"颜色":"红","尺码":"M"} */
    private String specJson;
    /** 规格展示文本，如 "红 / M" */
    private String specText;
    private BigDecimal price;
    private Integer stock;
    /** SKU 专属图片，可空（空则用商品主图） */
    private String image;
    /** 1=默认 SKU（单规格兼容） */
    private Integer isDefault;
    /** 1=启用 0=停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
