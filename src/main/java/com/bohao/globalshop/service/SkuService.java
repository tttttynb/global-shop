package com.bohao.globalshop.service;

import com.bohao.globalshop.dto.SkuDto;
import com.bohao.globalshop.entity.ProductSku;

import java.util.List;

/**
 * SKU 服务（Phase 1 - F1 规格系统）
 * <p>
 * 职责：
 * 1. SKU 的增删查改（商家发布/更新商品时整体替换）
 * 2. 存量单规格商品的默认 SKU 懒加载兼容（getOrCreateDefaultSku）
 * 3. SKU 级库存扣减：Redis Lua 原子预扣减 + MySQL 原子 UPDATE 双层防超卖
 * 4. 商品表 price/stock 冗余字段聚合同步（price=最低 SKU 价，stock=SKU 库存之和）
 * </p>
 */
public interface SkuService {

    /** 查询商品下所有启用的 SKU */
    List<ProductSku> listByProductId(Long productId);

    /**
     * 获取默认 SKU，不存在则根据商品 price/stock 自动创建（存量数据兼容）
     */
    ProductSku getOrCreateDefaultSku(Long productId);

    /**
     * 解析购买目标 SKU：
     * skuId 为空 → 默认 SKU；skuId 不存在/已停用 → 返回 null（由调用方决定报错或降级）
     */
    ProductSku resolveSku(Long productId, Long skuId);

    /** 商家发布/更新：用 dto 整体替换该商品的 SKU 列表，并同步商品聚合字段 */
    void replaceSkus(Long productId, List<SkuDto> skus);

    /** 删除商品时级联删除 SKU */
    void removeByProductId(Long productId);

    /** 重算并同步商品表冗余字段：price=最低 SKU 价，stock=SKU 库存之和 */
    void syncProductAggregate(Long productId);

    /**
     * 商品级改价/改库存同步到全部 SKU（商家简易编辑对话框无逐 SKU 输入）：
     * 价格统一应用到每个 SKU；库存按各 SKU 现有占比分配（保留 0 的售罄态），末位 SKU 吸收余数保证总和精确；
     * 最后重算商品聚合字段并刷新 Redis 库存。
     * 不这样做的话 SKU（真正的成交价来源）仍是旧价，下次聚合同步会把商品表改回去。
     */
    void applyProductLevelChange(Long productId, java.math.BigDecimal price, Integer stock);

    /** MySQL 原子扣减 SKU 库存（stock = stock - N WHERE stock >= N），成功返回 true */
    boolean deductStock(Long skuId, int quantity);

    /** 回补 SKU 库存（订单取消/退款） */
    void restoreStock(Long skuId, int quantity);

    /** 确保 Redis 中存在该 SKU 的库存 Key（缺失则从 DB 初始化），Key: seckill:stock:sku:{skuId} */
    void ensureRedisStock(Long skuId);

    /** Redis Lua 原子预扣减，成功返回 true（库存不足返回 false） */
    boolean deductRedisStock(Long skuId, int quantity);

    /** 回补 Redis 库存（仅当 Key 存在时） */
    void restoreRedisStock(Long skuId, int quantity);
}
