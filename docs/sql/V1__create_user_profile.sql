-- ============================================================
-- 用户消费画像表 — 个性化 AI 客服基础设施
-- 执行方式: 在 global_shop 数据库中直接执行此 SQL
-- 日期: 2026-07-16
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',

    -- 消费指标
    `total_spent` DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计消费金额（已成交订单总和）',
    `total_order_count` INT DEFAULT 0 COMMENT '累计已成交订单数',
    `avg_order_value` DECIMAL(10,2) DEFAULT 0.00 COMMENT '平均客单价 = total_spent / total_order_count',
    `recent_90d_spent` DECIMAL(12,2) DEFAULT 0.00 COMMENT '近90天消费金额',
    `recent_90d_order_count` INT DEFAULT 0 COMMENT '近90天订单数',
    `max_single_order` DECIMAL(10,2) DEFAULT 0.00 COMMENT '历史最高单笔消费',

    -- 分层标签
    `user_tier` ENUM('NEW','BUDGET','MID','PREMIUM') DEFAULT 'NEW' COMMENT '用户层级: NEW=新用户, BUDGET=普通, MID=中端, PREMIUM=高端',

    -- 偏好画像
    `preferred_categories` JSON COMMENT '偏好品类Top3，格式: {"categoryId": weight}，weight为归一化权重',
    `price_range_min` DECIMAL(10,2) DEFAULT NULL COMMENT '偏好价格带下限',
    `price_range_max` DECIMAL(10,2) DEFAULT NULL COMMENT '偏好价格带上限',
    `preferred_brands` JSON DEFAULT NULL COMMENT '偏好品牌，JSON数组',

    -- 活跃度
    `activity_score` DECIMAL(5,2) DEFAULT 0.00 COMMENT '活跃度评分 (0-100)',
    `last_order_time` DATETIME DEFAULT NULL COMMENT '最近下单时间',

    -- 元数据
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    INDEX `idx_user_tier` (`user_tier`),
    INDEX `idx_total_spent` (`total_spent`),
    INDEX `idx_recent_spent` (`recent_90d_spent`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消费画像表';


-- ============================================================
-- 用户行为日志表（可选，用于更精细的用户行为分析）
-- 在 Phase 4 优化阶段使用，当前可先创建或后续再执行
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_behavior_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `behavior_type` ENUM('SEARCH','VIEW','FAVORITE','ADD_CART','ORDER','REVIEW','CHAT') COMMENT '行为类型',
    `target_id` BIGINT DEFAULT NULL COMMENT '关联目标ID（商品ID/订单ID等）',
    `target_type` VARCHAR(32) DEFAULT NULL COMMENT '目标类型',
    `metadata` JSON DEFAULT NULL COMMENT '行为元数据（搜索关键词、停留时长等JSON）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',

    PRIMARY KEY (`id`),
    INDEX `idx_user_time` (`user_id`, `create_time`),
    INDEX `idx_behavior_time` (`behavior_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为日志表';
