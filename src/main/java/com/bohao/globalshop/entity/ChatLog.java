package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服对话日志（Phase 4）
 * <p>
 * 记录每次 AI 客服对话的关键信息，用于评估个性化效果：
 * userId + userTier → message → response → 是否调用 Tool → 响应耗时
 * </p>
 */
@Data
@TableName("chat_log")
public class ChatLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 当时用户层级（快照） */
    private String userTier;

    /** 用户消息 */
    private String userMessage;

    /** AI 回复 */
    private String aiResponse;

    /** 是否调用了 Tool（搜索商品/查订单） */
    @TableField("tool_called")
    private Boolean toolCalled;

    /** 调用的 Tool 名称（多个用逗号分隔） */
    private String toolNames;

    /** AI 推荐的商品ID列表（JSON 数组，从 Tool 返回结果中解析） */
    @TableField("recommended_product_ids")
    private String recommendedProductIds;

    /** 是否使用个性化 Agent */
    @TableField("is_personalized")
    private Boolean isPersonalized;

    /** 响应耗时（毫秒） */
    private Integer responseTimeMs;

    /** 消息字符数 */
    private Integer messageLength;

    /** 回复字符数 */
    private Integer responseLength;

    /** 用户是否点赞（null=未评价, true=赞, false=踩） */
    private Boolean feedback;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
