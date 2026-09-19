package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductSku;
import com.bohao.globalshop.entity.TaxRule;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductSkuMapper;
import com.bohao.globalshop.mapper.TaxRuleMapper;
import com.bohao.globalshop.service.TaxCalcService;
import com.bohao.globalshop.vo.TaxEstimateVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCalcServiceImpl implements TaxCalcService {

    private final TaxRuleMapper taxRuleMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    /** 税费计算开关（关闭后运费/税费均为 0，等价于旧行为） */
    @Value("${app.tax.enabled:true}")
    private boolean taxEnabled;

    /** 国际运费（固定档位，简化版：无重量/体积维度） */
    @Value("${app.tax.default-shipping-fee:29.00}")
    private BigDecimal defaultShippingFee;

    /** 满额包邮门槛（按商品金额） */
    @Value("${app.tax.free-shipping-threshold:199.00}")
    private BigDecimal freeShippingThreshold;

    /** 通用兜底税率（tax_rule 表连兜底行都没有时启用） */
    @Value("${app.tax.default-rate:0.0910}")
    private BigDecimal defaultRate;

    @Override
    public TaxEstimateVo estimate(Long productId, Long skuId, Integer quantity, String destination) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        int qty = quantity == null || quantity < 1 ? 1 : quantity;
        String dest = destination == null || destination.isBlank() ? "CN" : destination.toUpperCase();

        // 单价：指定 SKU 用 SKU 价（成交价来源），否则用商品聚合价
        BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
        if (skuId != null) {
            ProductSku sku = productSkuMapper.selectById(skuId);
            if (sku != null && sku.getPrice() != null) {
                unitPrice = sku.getPrice();
            }
        }

        BigDecimal itemAmount = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingFee = calcShippingFee(itemAmount);
        TaxRule rule = resolveRule(product.getCategoryId(), dest);

        // 计税价格 = 商品成交价 + 运费（财税〔2016〕18号简化：完税价格含运保费）
        BigDecimal taxAmount = taxEnabled
                ? (itemAmount.add(shippingFee)).multiply(rule.getTaxRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        TaxEstimateVo vo = new TaxEstimateVo();
        vo.setProductId(productId);
        vo.setQuantity(qty);
        vo.setDestination(dest);
        vo.setItemAmount(itemAmount);
        vo.setShippingFee(taxEnabled ? shippingFee : BigDecimal.ZERO);
        vo.setFreeShipping(shippingFee.compareTo(BigDecimal.ZERO) == 0);
        vo.setFreeShippingThreshold(freeShippingThreshold);
        vo.setTaxName(taxEnabled ? rule.getTaxName() : "免税");
        vo.setTaxRate(taxEnabled ? rule.getTaxRate() : BigDecimal.ZERO);
        vo.setTaxRatePercent(toPercent(taxEnabled ? rule.getTaxRate() : BigDecimal.ZERO));
        vo.setTaxAmount(taxAmount);
        vo.setTotalAmount(itemAmount.add(vo.getShippingFee()).add(taxAmount));
        vo.setTaxDescription(rule.getDescription());
        return vo;
    }

    @Override
    public TaxRule resolveRule(Long categoryId, String destination) {
        String dest = destination == null || destination.isBlank() ? "CN" : destination.toUpperCase();
        // 1. 精确匹配品类
        if (categoryId != null) {
            TaxRule rule = taxRuleMapper.selectOne(new QueryWrapper<TaxRule>()
                    .eq("category_id", categoryId).eq("destination", dest).last("LIMIT 1"));
            if (rule != null) {
                return rule;
            }
        }
        // 2. 通用兜底档（category_id=0）
        TaxRule general = taxRuleMapper.selectOne(new QueryWrapper<TaxRule>()
                .eq("category_id", 0).eq("destination", dest).last("LIMIT 1"));
        if (general != null) {
            return general;
        }
        // 3. 表为空的极端情况：配置兜底
        TaxRule fallback = new TaxRule();
        fallback.setCategoryId(0L);
        fallback.setDestination(dest);
        fallback.setTaxName("跨境综合税");
        fallback.setTaxRate(defaultRate);
        fallback.setDescription("配置兜底档位（tax_rule 表未初始化）");
        return fallback;
    }

    @Override
    public BigDecimal calcShippingFee(BigDecimal goodsAmount) {
        if (!taxEnabled) {
            return BigDecimal.ZERO;
        }
        if (goodsAmount == null) {
            goodsAmount = BigDecimal.ZERO;
        }
        return goodsAmount.compareTo(freeShippingThreshold) >= 0
                ? BigDecimal.ZERO
                : defaultShippingFee.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calcTaxAmount(Long categoryId, BigDecimal taxableAmount, String destination) {
        if (!taxEnabled || taxableAmount == null || taxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        TaxRule rule = resolveRule(categoryId, destination);
        return taxableAmount.multiply(rule.getTaxRate()).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<TaxRule> listRules() {
        return taxRuleMapper.selectList(new QueryWrapper<TaxRule>().orderByAsc("category_id"));
    }

    /** 0.0910 → "9.1%" */
    private String toPercent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}
