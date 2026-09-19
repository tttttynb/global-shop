package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外汇汇率（Phase 3 - F5 多币种）
 * <p>
 * rate_to_cny = 1 单位外币兑换人民币。定时任务多源拉取刷新（er-api → frankfurter → 静态兜底），
 * Redis 缓存 1 小时，下单时快照锁定到 trade_order.exchange_rate。
 * </p>
 */
@Data
@TableName("exchange_rate")
public class ExchangeRate {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 币种代码: USD/JPY/EUR/KRW/THB/GBP */
    private String currencyCode;
    /** 币种名称: 美元/日元... */
    private String currencyName;
    /** 货币符号: $/JP¥/€... */
    private String symbol;
    /** 1 单位外币 = X 人民币 */
    private BigDecimal rateToCny;
    /** 汇率来源: er-api/frankfurter/fallback/manual */
    private String source;
    private LocalDateTime updateTime;
}
