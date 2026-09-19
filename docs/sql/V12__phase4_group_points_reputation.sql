-- ============================================================
-- V12: Phase 4 — 社交拼团 + 会员积分 + AI口碑档案2.0
-- F7: group_buy_activity / group_buy_record / group_buy_member
-- F8: member_points / points_record + coupon.points_price
-- F9: review_intelligence
-- ============================================================

-- ---------- F7 社交拼团 ----------

-- 拼团活动（商家设置：成团人数 / 拼团价 / 有效期）
CREATE TABLE IF NOT EXISTS group_buy_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '拼团商品ID',
    sku_id BIGINT NULL COMMENT '指定SKU（可空=默认SKU）',
    shop_id BIGINT NULL COMMENT '店铺ID',
    group_price DECIMAL(12,2) NOT NULL COMMENT '拼团价（成团享此价）',
    required_members INT NOT NULL DEFAULT 2 COMMENT '成团人数',
    valid_hours INT NOT NULL DEFAULT 24 COMMENT '开团后有效时长（小时）',
    activity_stock INT NOT NULL DEFAULT 0 COMMENT '活动限量（0=不限，走SKU库存）',
    sold_count INT NOT NULL DEFAULT 0 COMMENT '已参团（已支付）件数',
    per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每人限参团次数',
    start_time DATETIME NULL COMMENT '活动开始时间（空=立即）',
    end_time DATETIME NULL COMMENT '活动截止时间（空=长期）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0已结束 1进行中',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_product (product_id),
    KEY idx_status (status),
    KEY idx_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动表（Phase 4 - F7）';

-- 团实例（一次开团 = 一条记录，成员参团挂在下面）
CREATE TABLE IF NOT EXISTS group_buy_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL COMMENT '拼团活动ID',
    leader_id BIGINT NOT NULL COMMENT '开团人用户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0拼团中 1已成团 2拼团失败(超时退款)',
    member_count INT NOT NULL DEFAULT 1 COMMENT '当前已支付成员数',
    required_members INT NOT NULL COMMENT '成团人数（活动快照）',
    expire_time DATETIME NOT NULL COMMENT '成团截止时间',
    form_time DATETIME NULL COMMENT '成团时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_activity (activity_id),
    KEY idx_status_expire (status, expire_time),
    KEY idx_leader (leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团团实例表（Phase 4 - F7）';

-- 团成员（开团人 + 参团人，各自绑定订单）
CREATE TABLE IF NOT EXISTS group_buy_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL COMMENT '团实例ID',
    activity_id BIGINT NOT NULL COMMENT '拼团活动ID',
    user_id BIGINT NOT NULL COMMENT '成员用户ID',
    order_id BIGINT NULL COMMENT '绑定的订单ID',
    pay_amount DECIMAL(12,2) NULL COMMENT '实付金额（拼团价）',
    is_leader TINYINT NOT NULL DEFAULT 0 COMMENT '1=开团人',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已退款/已取消',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME NULL,
    UNIQUE KEY uk_record_user (record_id, user_id),
    KEY idx_user (user_id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团成员表（Phase 4 - F7）';

-- 订单挂拼团标记
ALTER TABLE trade_order ADD COLUMN group_record_id BIGINT NULL COMMENT '拼团团实例ID（Phase 4 - F7）';

-- ---------- F8 会员积分 ----------

CREATE TABLE IF NOT EXISTS member_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    points INT NOT NULL DEFAULT 0 COMMENT '当前可用积分',
    total_earned INT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    growth DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '成长值（累计消费额，决定等级）',
    level INT NOT NULL DEFAULT 1 COMMENT '会员等级 1青铜 2白银 3黄金 4钻石',
    consecutive_days INT NOT NULL DEFAULT 0 COMMENT '连续签到天数',
    last_sign_date DATE NULL COMMENT '最近签到日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分账户表（Phase 4 - F8）';

CREATE TABLE IF NOT EXISTS points_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_amount INT NOT NULL COMMENT '积分变动（正=获得，负=消耗）',
    type VARCHAR(32) NOT NULL COMMENT 'EARN_ORDER/SIGN_IN/REVIEW/PROFILE/DEDUCT_ORDER/EXCHANGE_COUPON/REFUND_ORDER',
    related_id BIGINT NULL COMMENT '关联ID（订单/优惠券/画像）',
    description VARCHAR(255) NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表（Phase 4 - F8）';

-- 优惠券支持积分兑换（Phase 4 - F8：兑换商城）
ALTER TABLE coupon ADD COLUMN points_price INT NULL COMMENT '积分兑换价（>0 可积分兑换）';

-- 订单积分抵扣落库（Phase 4 - F8）
ALTER TABLE trade_order ADD COLUMN points_used INT NOT NULL DEFAULT 0 COMMENT '本单消耗积分';
ALTER TABLE trade_order ADD COLUMN points_deduction DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣金额';

-- 积分兑换商城种子：现有优惠券开放积分兑换价
UPDATE coupon SET points_price = 500 WHERE points_price IS NULL AND type = 1 AND discount_value <= 10 LIMIT 5;
UPDATE coupon SET points_price = 800 WHERE points_price IS NULL AND type = 2 LIMIT 5;

-- ---------- F9 AI口碑档案2.0 ----------

CREATE TABLE IF NOT EXISTS review_intelligence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    lang VARCHAR(8) NOT NULL DEFAULT 'zh' COMMENT '语言（zh/en/ja/ko/th）',
    content_json TEXT NOT NULL COMMENT '结构化档案JSON：pros/cons/bestFor/recommendScore/impressions/summary',
    review_count INT NOT NULL DEFAULT 0 COMMENT '生成时评价数',
    model_version VARCHAR(32) NULL COMMENT '生成模型版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_lang (product_id, lang)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI口碑档案表（Phase 4 - F9，持久化+多语言）';

-- ---------- 拼团活动种子（把现有在售商品挂上拼团玩法，便于演示） ----------
INSERT INTO group_buy_activity (product_id, sku_id, shop_id, group_price, required_members, valid_hours, per_user_limit, status)
SELECT p.id, NULL, p.shop_id, ROUND(p.price * 0.85, 2), 3, 24, 1, 1
FROM product p
WHERE p.status = 1 AND p.price > 10
ORDER BY p.id
LIMIT 5;
