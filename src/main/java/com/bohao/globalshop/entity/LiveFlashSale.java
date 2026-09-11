package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 直播间闪购秒杀活动（Phase 2 - F3）
 * <p>
 * 库存模型：活动开始时从 SKU 预占 totalQty；抢购扣独立 Redis 池 flash:stock:{id} + remain_qty；
 * 结束/取消时 remainQty 回补 SKU。
 * </p>
 */
@Data
@TableName("live_flash_sale")
public class LiveFlashSale {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long liveRoomId;
    private Long productId;
    /** 从该 SKU 预占库存（默认 SKU 兼容单规格） */
    private Long skuId;
    private BigDecimal flashPrice;
    /** 原价快照（展示划线价） */
    private BigDecimal originalPrice;
    private Integer totalQty;
    private Integer remainQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0=进行中 1=已结束 2=已取消 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
