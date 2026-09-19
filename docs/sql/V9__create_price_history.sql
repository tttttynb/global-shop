-- =============================================================
-- V9: 价格历史表（Phase 1 - F2 价格走势图）
-- 商品发布/改价时落一条记录，前端详情页展示近90天价格曲线
-- =============================================================

CREATE TABLE IF NOT EXISTS price_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT        NOT NULL COMMENT '商品ID',
    price       DECIMAL(10,2) NOT NULL COMMENT '记录时点的商品最低价',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    INDEX idx_product_time (product_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品价格历史表';
