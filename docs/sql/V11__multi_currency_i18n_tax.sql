-- =============================================================
-- V11: 多币种多语言国际化 + 跨境税费计算（Phase 3 - F5/F6）
--
-- 1. product 增加原币定价字段：original_currency / original_price / origin_country
--    商品以原币（USD/JPY/EUR/KRW/THB/GBP）定价，price 字段仍为人民币成交价，
--    前台按实时汇率展示"人民币参考价"（展示层标注参考价）。
-- 2. exchange_rate 汇率表：定时任务多源拉取（open.er-api.com / frankfurter 兜底），
--    Redis 缓存 1 小时；表内预置静态兜底汇率，API 全挂也能工作。
-- 3. product_translation 商品译文表：商品发布/编辑后事件驱动异步 AI 翻译四语（en/ja/ko/th）。
-- 4. tax_rule 跨境税率规则表（品类 × 目的国简化版，常见行邮税/跨境综合税档位，无需对接海关）。
-- 5. trade_order 增加结算快照列：下单时锁定汇率 + 运费/税费明细，保证展示与结算一致可审计。
-- =============================================================

-- ---------- 1. 商品原币定价 ----------
ALTER TABLE product
    ADD COLUMN original_currency VARCHAR(8)    DEFAULT 'CNY' COMMENT '原币币种代码: CNY/USD/JPY/EUR/KRW/THB/GBP' AFTER price,
    ADD COLUMN original_price    DECIMAL(12,2) DEFAULT NULL  COMMENT '原币定价（如日本商品 3980 日元）' AFTER original_currency,
    ADD COLUMN origin_country    VARCHAR(32)   DEFAULT NULL  COMMENT '发货国家/地区，如 日本、美国' AFTER original_price;

-- ---------- 2. 汇率表（rate_to_cny = 1 单位外币兑人民币） ----------
CREATE TABLE IF NOT EXISTS exchange_rate (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_code VARCHAR(8)    NOT NULL COMMENT '币种代码: USD/JPY/EUR/KRW/THB/GBP',
    currency_name VARCHAR(32)   NOT NULL COMMENT '币种名称: 美元/日元...',
    symbol        VARCHAR(8)    NOT NULL COMMENT '货币符号: $/JP¥/€...',
    rate_to_cny   DECIMAL(18,6) NOT NULL COMMENT '1 单位外币 = X 人民币',
    source        VARCHAR(32)   DEFAULT 'fallback' COMMENT '汇率来源: er-api/frankfurter/fallback/manual',
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_currency (currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外汇汇率表';

-- 静态兜底汇率（定时任务刷新后被覆盖；API 全挂时保证系统可用）
INSERT INTO exchange_rate (currency_code, currency_name, symbol, rate_to_cny, source) VALUES
    ('USD', '美元',   '$',   7.100000, 'fallback'),
    ('EUR', '欧元',   '€',   7.800000, 'fallback'),
    ('GBP', '英镑',   '£',   9.050000, 'fallback'),
    ('JPY', '日元',   'JP¥', 0.048000, 'fallback'),
    ('KRW', '韩元',   '₩',   0.005200, 'fallback'),
    ('THB', '泰铢',   '฿',   0.210000, 'fallback')
ON DUPLICATE KEY UPDATE currency_code = currency_code;

-- ---------- 3. 商品译文表 ----------
CREATE TABLE IF NOT EXISTS product_translation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT      NOT NULL COMMENT '商品ID',
    lang        VARCHAR(8)  NOT NULL COMMENT '语言: en/ja/ko/th',
    title       VARCHAR(512) DEFAULT NULL COMMENT '译文标题',
    description TEXT         DEFAULT NULL COMMENT '译文描述',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_lang (product_id, lang)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品多语言译文表（AI 异步翻译）';

-- ---------- 4. 跨境税率规则表（品类 × 目的国 简化版） ----------
CREATE TABLE IF NOT EXISTS tax_rule (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '品类ID，0=通用兜底规则',
    destination  VARCHAR(32)  NOT NULL DEFAULT 'CN' COMMENT '目的国代码，默认 CN（进口到中国）',
    tax_name     VARCHAR(64)  NOT NULL COMMENT '税种名称: 跨境综合税/行邮税',
    tax_rate     DECIMAL(6,4) NOT NULL COMMENT '税率，如 0.0910 = 9.1%',
    description  VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_dest (category_id, destination)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨境税费规则表';

-- 通用兜底：跨境电商综合税 9.1%（增值税 13% × 70% 折让，单次交易限值内）
INSERT INTO tax_rule (category_id, destination, tax_name, tax_rate, description) VALUES
    (0, 'CN', '跨境综合税', 0.0910, '通用档位：跨境电商零售进口综合税（增值税70%折让）')
ON DUPLICATE KEY UPDATE tax_rate = VALUES(tax_rate);

-- 品类专属档位（按品类名模糊匹配挂载，存在才插入，幂等）
-- 高档化妆品：含消费税档 23.06%
INSERT INTO tax_rule (category_id, destination, tax_name, tax_rate, description)
SELECT c.id, 'CN', '跨境综合税(含消费税)', 0.2306, '高档化妆品：完税价格≥10元/毫升(克) 征收消费税'
FROM category c
WHERE (c.name LIKE '%美妆%' OR c.name LIKE '%化妆%' OR c.name LIKE '%个护%')
  AND NOT EXISTS (SELECT 1 FROM tax_rule t WHERE t.category_id = c.id AND t.destination = 'CN');

-- 服饰鞋包：行邮税 20% 档
INSERT INTO tax_rule (category_id, destination, tax_name, tax_rate, description)
SELECT c.id, 'CN', '行邮税', 0.2000, '纺织品/箱包/鞋靴：行邮税第二档 20%'
FROM category c
WHERE (c.name LIKE '%服饰%' OR c.name LIKE '%服装%' OR c.name LIKE '%鞋%' OR c.name LIKE '%箱包%')
  AND NOT EXISTS (SELECT 1 FROM tax_rule t WHERE t.category_id = c.id AND t.destination = 'CN');

-- 贵重首饰/高档手表：行邮税 50% 档
INSERT INTO tax_rule (category_id, destination, tax_name, tax_rate, description)
SELECT c.id, 'CN', '行邮税', 0.5000, '贵重首饰及珠宝玉石/高档手表：行邮税第三档 50%'
FROM category c
WHERE (c.name LIKE '%珠宝%' OR c.name LIKE '%首饰%' OR c.name LIKE '%腕表%' OR c.name LIKE '%手表%')
  AND NOT EXISTS (SELECT 1 FROM tax_rule t WHERE t.category_id = c.id AND t.destination = 'CN');

-- ---------- 5. 订单结算快照（锁汇 + 税费明细） ----------
ALTER TABLE trade_order
    ADD COLUMN currency        VARCHAR(8)    DEFAULT 'CNY' COMMENT '订单原币币种（快照）' AFTER discount_amount,
    ADD COLUMN exchange_rate   DECIMAL(18,6) DEFAULT NULL  COMMENT '下单时锁定的汇率 1外币=X人民币（快照）' AFTER currency,
    ADD COLUMN original_amount DECIMAL(12,2) DEFAULT NULL  COMMENT '原币金额（快照，用于审计对账）' AFTER exchange_rate,
    ADD COLUMN shipping_fee    DECIMAL(10,2) DEFAULT 0.00  COMMENT '国际运费' AFTER original_amount,
    ADD COLUMN tax_fee         DECIMAL(10,2) DEFAULT 0.00  COMMENT '跨境税费' AFTER shipping_fee;
