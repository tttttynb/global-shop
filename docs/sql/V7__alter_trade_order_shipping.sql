-- ============================================
-- V7: trade_order 增加物流追踪字段
-- 记录物流公司和运单号，支持快速查询
-- ============================================
ALTER TABLE trade_order
    ADD COLUMN carrier_name VARCHAR(50) COMMENT '物流公司名称',
    ADD COLUMN tracking_number VARCHAR(100) COMMENT '运单号',
    ADD COLUMN shipped_at DATETIME COMMENT '发货时间';

-- 为历史已发货的订单补充发货时间
UPDATE trade_order
SET shipped_at = update_time
WHERE status >= 3 AND shipped_at IS NULL;
