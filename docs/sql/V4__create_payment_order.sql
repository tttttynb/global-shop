-- ============================================
-- V4: 创建支付订单表
-- 用于记录每一笔支付交易全生命周期
-- ============================================
CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '关联的订单ID(trade_order.id)',
    user_id BIGINT NOT NULL COMMENT '支付用户ID',
    channel INT NOT NULL COMMENT '支付渠道: 0-余额 1-支付宝 2-微信 3-Stripe',
    channel_name VARCHAR(50) NOT NULL COMMENT '支付渠道名称',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status INT DEFAULT 0 COMMENT '支付状态: 0-待支付 1-成功 2-失败 3-已退款 4-已关闭',
    transaction_id VARCHAR(128) COMMENT '网关交易流水号(第三方返回)',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号(唯一, 格式: GS+时间戳+随机数)',
    callback_data TEXT COMMENT '回调原始数据(JSON, 用于对账排查)',
    callback_time DATETIME COMMENT '回调时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_transaction_id (transaction_id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    INDEX idx_order (order_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';
