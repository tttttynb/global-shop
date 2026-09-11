package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_order_item")
public class TradeOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId; // 关联的主订单 ID
    private Long productId;
    /** 购买的 SKU ID（下单快照，存量数据可为空） */
    private Long skuId;
    /** 规格快照文本，如 "红 / M" */
    private String skuSpec;
    private String productName;
    private String coverImage;
    private BigDecimal price; // 购买时的单价（快照）
    private Integer quantity;
    private BigDecimal totalAmount; // 小计金额
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
