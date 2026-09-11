package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品价格历史（Phase 1 - F2 价格走势图）
 * 商品发布/改价时落一条记录，详情页展示近 90 天价格曲线。
 */
@Data
@TableName("price_history")
public class PriceHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    /** 记录时点的商品最低价 */
    private BigDecimal price;
    private LocalDateTime createTime;
}
