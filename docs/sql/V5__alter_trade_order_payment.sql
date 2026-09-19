-- ============================================
-- V5: trade_order 增加支付追踪字段
-- 记录订单的支付渠道和支付时间
-- ============================================
ALTER TABLE trade_order
    ADD COLUMN payment_type VARCHAR(50) COMMENT '支付渠道名称(如: 余额支付/支付宝/微信支付/Stripe)',
    ADD COLUMN payment_id BIGINT COMMENT '关联的支付订单ID(payment_order.id)',
    ADD COLUMN pay_time DATETIME COMMENT '支付完成时间';

-- 为历史已支付的订单补充支付信息
UPDATE trade_order
SET payment_type = '余额支付',
    pay_time = update_time
WHERE status >= 1 AND payment_type IS NULL;
