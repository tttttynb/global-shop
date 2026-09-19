package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.ExchangeRate;

import java.math.BigDecimal;
import java.util.List;

/**
 * 外汇汇率服务（Phase 3 - F5 多币种）
 * <p>
 * 数据链路：定时任务多源拉取（er-api → frankfurter）→ MySQL 落库 → Redis 缓存 1 小时。
 * 读取优先级：Redis → MySQL → 配置兜底汇率，保证汇率数据源不稳定时系统仍可用。
 * 展示层统一标注"参考价"，下单时快照锁汇写入订单。
 * </p>
 */
public interface ExchangeRateService {

    /**
     * 获取 1 单位外币兑人民币汇率（CNY 恒返回 1）
     * @param currencyCode 币种代码，如 USD/JPY
     * @return rate_to_cny，未知币种返回 null
     */
    BigDecimal getRateToCny(String currencyCode);

    /**
     * 全部币种汇率列表（含 CNY 基准行，供前端币种切换器展示）
     */
    List<ExchangeRate> getAllRates();

    /**
     * 金额换算
     * @param amount 金额
     * @param from 源币种
     * @param to 目标币种
     * @return 换算后金额（保留 2 位小数），币种无效返回 null
     */
    BigDecimal convert(BigDecimal amount, String from, String to);

    /**
     * 刷新汇率：依次尝试多个免费汇率 API，成功后落库 + 写 Redis 缓存。
     * 全部失败时保留库内旧汇率（旧数据 > 无数据），仅当库为空时启用配置兜底。
     * @return 本次成功刷新的币种数量
     */
    int refreshRates();

    /**
     * 支持的币种代码列表（不含 CNY）
     */
    List<String> getSupportedCurrencies();
}
