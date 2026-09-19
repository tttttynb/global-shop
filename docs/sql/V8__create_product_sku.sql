-- =============================================================
-- V8: SKU/规格系统（Phase 1 - F1）
-- 1. 新增 product_sku 表：商品多规格，每个 SKU 独立价格/库存/图片
-- 2. cart_item 增加 sku_id / sku_spec
-- 3. trade_order_item 增加 sku_id / sku_spec（下单快照）
-- 兼容策略：存量单规格商品由后端懒加载自动生成 is_default=1 的默认 SKU
-- =============================================================

CREATE TABLE IF NOT EXISTS product_sku (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT        NOT NULL COMMENT '所属商品ID',
    spec_json   VARCHAR(1000) DEFAULT '{}' COMMENT '规格JSON，如 {"颜色":"红","尺码":"M"}',
    spec_text   VARCHAR(255)  DEFAULT '' COMMENT '规格展示文本，如 "红 / M"',
    price       DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
    stock       INT           NOT NULL DEFAULT 0 COMMENT 'SKU库存',
    image       VARCHAR(500)  DEFAULT NULL COMMENT 'SKU专属图片（可空，空则用商品主图）',
    is_default  TINYINT       NOT NULL DEFAULT 0 COMMENT '1=默认SKU（单规格兼容）',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

ALTER TABLE cart_item
    ADD COLUMN sku_id   BIGINT      DEFAULT NULL COMMENT '购买的SKU ID' AFTER product_id,
    ADD COLUMN sku_spec VARCHAR(255) DEFAULT NULL COMMENT '规格快照文本' AFTER sku_id;

ALTER TABLE trade_order_item
    ADD COLUMN sku_id   BIGINT      DEFAULT NULL COMMENT '购买的SKU ID' AFTER product_id,
    ADD COLUMN sku_spec VARCHAR(255) DEFAULT NULL COMMENT '规格快照文本' AFTER sku_id;
