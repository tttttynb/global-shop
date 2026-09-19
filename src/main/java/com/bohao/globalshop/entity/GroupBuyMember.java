package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拼团成员（Phase 4 - F7）：开团人 + 参团人，各自绑定订单
 */
@Data
@TableName("group_buy_member")
public class GroupBuyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团实例ID */
    private Long recordId;

    /** 拼团活动ID */
    private Long activityId;

    /** 成员用户ID */
    private Long userId;

    /** 绑定的订单ID */
    private Long orderId;

    /** 实付金额（拼团价） */
    private BigDecimal payAmount;

    /** 1=开团人 */
    private Integer isLeader;

    /** 0待支付 1已支付 2已退款/已取消 */
    private Integer status;

    private LocalDateTime joinTime;
    private LocalDateTime payTime;
}
