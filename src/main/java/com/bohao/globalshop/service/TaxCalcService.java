package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.TaxRule;
import com.bohao.globalshop.vo.TaxEstimateVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨境税费计算服务（Phase 3 - F6）
 * <p>
 * 税率规则表（品类 × 目的国简化版）+ 国际运费规则（满额包邮）。
 * 详情页展示"预估到手价"明细，下单链路由 OrderService 调用落快照。
 * </p>
 */
public interface TaxCalcService {

    /**
     * 详情页到手价试算
     * @param productId 商品ID
     * @param skuId 可选，指定 SKU 用 SKU 价，否则用商品聚合价
     * @param quantity 数量
     * @param destination 目的国（默认 CN）
     * @return 试算明细，商品不存在返回 null
     */
    TaxEstimateVo estimate(Long productId, Long skuId, Integer quantity, String destination);

    /**
     * 解析品类适用的税率规则：优先精确匹配品类，未命中落到通用兜底档（category_id=0）
     */
    TaxRule resolveRule(Long categoryId, String destination);

    /**
     * 国际运费：商品金额满 free-shipping-threshold 包邮，否则收固定运费
     */
    BigDecimal calcShippingFee(BigDecimal goodsAmount);

    /**
     * 单件商品税费 = 计税金额（商品成交价 + 分摊运费）× 品类税率
     * @param categoryId 品类ID
     * @param taxableAmount 计税金额（人民币）
     * @param destination 目的国
     */
    BigDecimal calcTaxAmount(Long categoryId, BigDecimal taxableAmount, String destination);

    /**
     * 全部税率规则（管理/展示用）
     */
    List<TaxRule> listRules();
}
