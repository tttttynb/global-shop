-- ============================================================
-- V8: 消息通知表
-- 站内信系统核心表，支持订单状态/优惠券/直播/促销/系统通知
-- ============================================================

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    type VARCHAR(30) NOT NULL COMMENT '通知类型: ORDER_STATUS/COUPON_EXPIRE/LIVE_START/PROMOTION/SYSTEM',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知正文',
    target_type VARCHAR(30) COMMENT '跳转类型: ORDER/PRODUCT/LIVE/COUPON/NONE',
    target_id BIGINT COMMENT '跳转目标ID',
    is_read TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
    read_time DATETIME COMMENT '阅读时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    INDEX idx_user_read (user_id, is_read, create_time),
    INDEX idx_user_type (user_id, type, create_time),
    INDEX idx_create_time (create_time)
) COMMENT '消息通知表';
