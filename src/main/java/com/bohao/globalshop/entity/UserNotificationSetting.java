package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知设置 — 每位用户一条记录，控制通知开关
 */
@Data
@TableName("user_notification_setting")
public class UserNotificationSetting {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Integer emailOrderUpdate;   // 邮件-订单更新 0关1开
    private Integer emailPromotion;     // 邮件-促销活动
    private Integer siteOrderUpdate;    // 站内-订单更新
    private Integer siteLiveRemind;     // 站内-直播提醒
    private Integer sitePromotion;      // 站内-促销活动
    private Integer siteCoupon;         // 站内-优惠券提醒
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
