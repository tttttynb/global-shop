package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知实体 — 站内信核心表
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;          // ORDER_STATUS / COUPON_EXPIRE / LIVE_START / PROMOTION / SYSTEM
    private String title;
    private String content;
    private String targetType;    // ORDER / PRODUCT / LIVE / COUPON / NONE
    private Long targetId;
    private Integer isRead;       // 0-未读 1-已读
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
