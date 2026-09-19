package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拼团活动（Phase 4 - F7）：商家设置成团人数 / 拼团价 / 有效期
 */
@Data
@TableName("group_buy_activity")
public class GroupBuyActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 拼团商品ID */
    private Long productId;

    /** 指定SKU（可空=默认SKU） */
    private Long skuId;

    /** 店铺ID */
    private Long shopId;

    /** 拼团价（成团享此价） */
    private BigDecimal groupPrice;

    /** 成团人数 */
    private Integer requiredMembers;

    /** 开团后有效时长（小时） */
    private Integer validHours;

    /** 活动限量（0=不限，走SKU库存） */
    private Integer activityStock;

    /** 已参团（已支付）件数 */
    private Integer soldCount;

    /** 每人限参团次数 */
    private Integer perUserLimit;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 0已结束 1进行中 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
