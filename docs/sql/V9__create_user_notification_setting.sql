-- ============================================================
-- V9: 用户通知设置表
-- 每位用户一条记录，控制各渠道通知的开关
-- ============================================================

CREATE TABLE user_notification_setting (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    email_order_update TINYINT DEFAULT 1 COMMENT '邮件-订单更新',
    email_promotion TINYINT DEFAULT 1 COMMENT '邮件-促销活动',
    site_order_update TINYINT DEFAULT 1 COMMENT '站内-订单更新',
    site_live_remind TINYINT DEFAULT 1 COMMENT '站内-直播提醒',
    site_promotion TINYINT DEFAULT 1 COMMENT '站内-促销活动',
    site_coupon TINYINT DEFAULT 1 COMMENT '站内-优惠券提醒',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户通知设置表';
