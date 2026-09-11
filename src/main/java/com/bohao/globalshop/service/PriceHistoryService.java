package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.PriceHistory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格历史服务（Phase 1 - F2 价格走势图）
 */
public interface PriceHistoryService {

    /**
     * 记录价格快照：仅当与最近一条记录价格不同时落库（防重复膨胀）
     */
    void recordPrice(Long productId, BigDecimal price);

    /**
     * 查询价格历史（按时间升序），days 为最近 N 天，最多返回 200 条
     */
    List<PriceHistory> getHistory(Long productId, int days);
}
