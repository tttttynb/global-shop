-- =============================================================
-- V10: 直播间闪购秒杀（Phase 2 - F3）
-- 1. 新增 live_flash_sale 表：主播在直播间发起的限时秒杀活动
-- 2. trade_order 增加 order_source / flash_sale_id：标记秒杀订单来源，便于转化统计
--
-- 库存模型（闭环一致）：
--   活动开始 → 从 SKU 库存预占 total_qty（Redis Lua + DB 原子扣减）
--   用户抢购 → 扣活动独立库存池 flash:stock:{saleId}（Redis Lua + remain_qty 原子扣减）
--   活动结束/取消 → 未售出的 remain_qty 回补到 SKU
--   秒杀订单取消 → 走现有 cancelSingleOrder 回补 SKU（预占来源，语义正确）
-- =============================================================

CREATE TABLE IF NOT EXISTS live_flash_sale (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    live_room_id  BIGINT        NOT NULL COMMENT '直播间ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    sku_id        BIGINT        DEFAULT NULL COMMENT 'SKU ID（从该SKU预占库存）',
    flash_price   DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    original_price DECIMAL(10,2) DEFAULT NULL COMMENT '原价快照（展示划线价）',
    total_qty     INT           NOT NULL COMMENT '秒杀总量',
    remain_qty    INT           NOT NULL COMMENT '剩余量',
    start_time    DATETIME      NOT NULL COMMENT '开始时间',
    end_time      DATETIME      NOT NULL COMMENT '截止时间',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0=进行中 1=已结束 2=已取消',
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_room_status (live_room_id, status),
    INDEX idx_status_end (status, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间闪购秒杀活动表';

ALTER TABLE trade_order
    ADD COLUMN order_source   VARCHAR(32) DEFAULT 'NORMAL' COMMENT '订单来源: NORMAL/LIVE_FLASH' AFTER status,
    ADD COLUMN flash_sale_id  BIGINT      DEFAULT NULL COMMENT '关联的秒杀活动ID' AFTER order_source;
