package com.bohao.globalshop.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知 VO — 返回给前端的通知数据
 */
@Data
public class NotificationVo {
    private Long id;
    private String type;
    private String typeLabel;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
    private String timeAgo;         // 前端辅助："3分钟前"、"昨天 14:30"
}
