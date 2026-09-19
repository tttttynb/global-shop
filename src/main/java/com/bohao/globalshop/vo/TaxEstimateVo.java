package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 跨境到手价试算结果（Phase 3 - F6）
 * <p>
 * 预估到手价 = 商品价 + 国际运费 + 跨境综合税
 * </p>
 */
@Data
public class TaxEstimateVo {
    private Long productId;
    private Integer quantity;
    /** 计价币种（试算统一以人民币计） */
    private String currency = "CNY";
    /** 目的国代码 */
    private String destination = "CN";
    /** 商品金额（单价 × 数量） */
    private BigDecimal itemAmount;
    /** 国际运费（满额包邮时为 0） */
    private BigDecimal shippingFee;
    /** 是否已免运费 */
    private Boolean freeShipping;
    /** 免邮门槛 */
    private BigDecimal freeShippingThreshold;
    /** 税种名称: 跨境综合税/行邮税 */
    private String taxName;
    /** 税率（0.0910 = 9.1%） */
    private BigDecimal taxRate;
    /** 税率百分比展示文本，如 "9.1%" */
    private String taxRatePercent;
    /** 税费金额 */
    private BigDecimal taxAmount;
    /** 预估到手价 = 商品金额 + 运费 + 税费 */
    private BigDecimal totalAmount;
    /** 规则说明（档位来源） */
    private String taxDescription;
    /** 免责说明 */
    private String note = "预估仅供参考，实际以海关审定为准；税率档位按跨境电商零售进口政策简化计算";
}
