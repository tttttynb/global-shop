-- ========================================
-- 全球购商城功能完善 - 数据库升级脚本
-- ========================================

-- Task 1: 用户体系增强
ALTER TABLE `user` ADD COLUMN `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL' AFTER `nickname`;
ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号' AFTER `avatar`;
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱' AFTER `phone`;

CREATE TABLE IF NOT EXISTS `user_address` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
    `province` VARCHAR(50) DEFAULT NULL COMMENT '省份',
    `city` VARCHAR(50) DEFAULT NULL COMMENT '城市',
    `district` VARCHAR(50) DEFAULT NULL COMMENT '区/县',
    `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否默认地址 0-否 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

-- Task 2: 商品管理增强
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
    `icon` VARCHAR(200) DEFAULT NULL COMMENT '分类图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序值',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

CREATE TABLE IF NOT EXISTS `product_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';

ALTER TABLE `product` ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '分类ID' AFTER `shop_id`;
ALTER TABLE `product` ADD COLUMN `sales_count` INT DEFAULT 0 COMMENT '销量' AFTER `stock`;

-- Task 3: 退货售后流程
CREATE TABLE IF NOT EXISTS `refund_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `order_item_id` BIGINT NOT NULL COMMENT '关联订单详情ID',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `shop_id` BIGINT NOT NULL COMMENT '店铺ID',
    `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    `reason` VARCHAR(500) NOT NULL COMMENT '退款原因',
    `images` VARCHAR(1000) DEFAULT NULL COMMENT '凭证图片，逗号分隔',
    `status` TINYINT DEFAULT 0 COMMENT '退款状态：0-待审核 1-商家同意 2-商家拒绝 3-退款成功 4-已取消',
    `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款订单表';

-- Task 4: 优惠券与营销
CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL COMMENT '店铺ID',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `type` TINYINT NOT NULL COMMENT '类型：1-满减券 2-折扣券',
    `discount_value` DECIMAL(10,2) NOT NULL COMMENT '优惠值：满减金额或折扣比例',
    `min_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '最低消费金额',
    `start_time` DATETIME NOT NULL COMMENT '生效时间',
    `end_time` DATETIME NOT NULL COMMENT '失效时间',
    `total_count` INT NOT NULL COMMENT '发行总量',
    `remain_count` INT NOT NULL COMMENT '剩余数量',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-未使用 1-已使用 2-已过期',
    `use_time` DATETIME DEFAULT NULL COMMENT '使用时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

ALTER TABLE `trade_order` ADD COLUMN `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID' AFTER `status`;
ALTER TABLE `trade_order` ADD COLUMN `discount_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额' AFTER `coupon_id`;

-- Task 6: 订单体验优化
ALTER TABLE `trade_order` ADD COLUMN `receiver_name` VARCHAR(50) DEFAULT NULL COMMENT '收货人姓名' AFTER `discount_amount`;
ALTER TABLE `trade_order` ADD COLUMN `receiver_phone` VARCHAR(20) DEFAULT NULL COMMENT '收货人电话' AFTER `receiver_name`;
ALTER TABLE `trade_order` ADD COLUMN `receiver_address` VARCHAR(300) DEFAULT NULL COMMENT '收货地址' AFTER `receiver_phone`;
