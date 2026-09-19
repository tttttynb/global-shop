package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨境税费规则（Phase 3 - F6 跨境税费计算器）
 * <p>
 * 品类 × 目的国简化版规则表（常见行邮税/跨境综合税档位，无需对接海关）。
 * category_id=0 为通用兜底规则；查询时优先精确匹配品类，未命中落到通用档。
 * </p>
 */
@Data
@TableName("tax_rule")
public class TaxRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 品类ID，0=通用兜底规则 */
    private Long categoryId;
    /** 目的国代码，默认 CN（进口到中国） */
    private String destination;
    /** 税种名称: 跨境综合税/行邮税 */
    private String taxName;
    /** 税率，如 0.0910 = 9.1% */
    private BigDecimal taxRate;
    /** 规则说明 */
    private String description;
    private LocalDateTime createTime;
}
