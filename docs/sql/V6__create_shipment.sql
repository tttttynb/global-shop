-- ============================================
-- V6: 创建物流信息表
-- 记录每笔订单的发货物流全生命周期
-- ============================================
CREATE TABLE IF NOT EXISTS shipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '关联订单ID(trade_order.id)',
    carrier_name VARCHAR(50) NOT NULL COMMENT '物流公司名称',
    carrier_code VARCHAR(20) COMMENT '物流公司编码(SF/ZTO/YTO/DHL/FEDEX/UPS)',
    tracking_number VARCHAR(100) NOT NULL COMMENT '运单号',
    status TINYINT DEFAULT 0 COMMENT '物流状态: 0-待揽收 1-运输中 2-派送中 3-已签收 4-异常',
    current_location VARCHAR(255) COMMENT '当前位置描述',
    estimated_delivery DATE COMMENT '预计送达日期',
    delivered_time DATETIME COMMENT '签收时间',
    tracking_data TEXT COMMENT '完整物流轨迹(JSON数组)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发货时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order (order_id),
    INDEX idx_tracking (tracking_number),
    INDEX idx_carrier (carrier_code, tracking_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流信息表';
