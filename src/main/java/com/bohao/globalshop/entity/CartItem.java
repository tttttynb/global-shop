package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart_item")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    /** 购买的 SKU ID（存量数据可为空，空则走默认 SKU 兼容逻辑） */
    private Long skuId;
    /** 规格快照文本，如 "红 / M" */
    private String skuSpec;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
