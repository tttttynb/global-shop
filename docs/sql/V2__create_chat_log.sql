-- Phase 4: AI 客服对话日志表
-- 用于收集个性化效果数据，支持 A/B 对比分析
CREATE TABLE IF NOT EXISTS chat_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_tier VARCHAR(16) COMMENT '当时用户层级快照',
    user_message TEXT COMMENT '用户消息',
    ai_response MEDIUMTEXT COMMENT 'AI回复',
    tool_called TINYINT(1) DEFAULT 0 COMMENT '是否调用了Tool',
    tool_names VARCHAR(255) COMMENT '调用的Tool名称',
    recommended_product_ids VARCHAR(512) COMMENT '推荐商品ID列表',
    is_personalized TINYINT(1) DEFAULT 1 COMMENT '是否使用个性化Agent',
    response_time_ms INT COMMENT '响应耗时ms',
    message_length INT COMMENT '消息字符数',
    response_length INT COMMENT '回复字符数',
    feedback TINYINT(1) DEFAULT NULL COMMENT '用户评价: null=未评, 1=赞, 0=踩',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_tier (user_tier),
    INDEX idx_is_personalized (is_personalized),
    INDEX idx_create_time (create_time),
    INDEX idx_tier_time (user_tier, create_time)
) COMMENT 'AI客服对话日志表';
