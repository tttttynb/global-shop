package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户消费画像实体
 * <p>
 * 存储用户的消费行为摘要，用于 AI 客服个性化推荐
 */
@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户ID，唯一 */
    private Long userId;

    // ======================== 消费指标 ========================

    /** 累计消费金额（已成交订单总和） */
    private BigDecimal totalSpent;

    /** 累计订单数（已成交） */
    private Integer totalOrderCount;

    /** 平均客单价 = totalSpent / totalOrderCount */
    private BigDecimal avgOrderValue;

    /** 近90天消费金额 */
    @TableField("recent_90d_spent")
    private BigDecimal recent90dSpent;

    /** 近90天订单数 */
    @TableField("recent_90d_order_count")
    private Integer recent90dOrderCount;

    /** 历史最高单笔消费 */
    private BigDecimal maxSingleOrder;

    // ======================== 分层标签 ========================

    /** 用户层级：NEW / BUDGET / MID / PREMIUM */
    private String userTier;

    // ======================== 偏好画像 ========================

    /** 偏好品类 Top3，JSON 格式：{"1": 0.8, "3": 0.5, "5": 0.3}，value 为归一化权重 */
    private String preferredCategories;

    /** 偏好价格带下限 */
    private BigDecimal priceRangeMin;

    /** 偏好价格带上限 */
    private BigDecimal priceRangeMax;

    /** 偏好品牌，JSON 数组 */
    private String preferredBrands;

    // ======================== 活跃度 ========================

    /** 活跃度评分 (0-100) */
    private BigDecimal activityScore;

    /** 最近下单时间 */
    private LocalDateTime lastOrderTime;

    // ======================== 元数据 ========================

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
