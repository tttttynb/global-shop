package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.ExchangeRate;
import com.bohao.globalshop.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外汇汇率接口（Phase 3 - F5 多币种）
 * <p>
 * 前端币种切换器加载全量汇率（Redis 缓存 1 小时），商品详情页做原币 → 人民币参考价实时换算。
 * </p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/forex")
public class ForexController {

    private final ExchangeRateService exchangeRateService;

    /**
     * 全量汇率列表（含 CNY 基准行），前端启动/切换币种时加载
     */
    @GetMapping("/rates")
    public Result<List<ExchangeRate>> getRates() {
        return Result.success(exchangeRateService.getAllRates());
    }

    /**
     * 金额换算：GET /api/forex/convert?amount=3980&from=JPY&to=CNY
     */
    @GetMapping("/convert")
    public Result<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "CNY") String from,
            @RequestParam(defaultValue = "CNY") String to) {
        BigDecimal converted = exchangeRateService.convert(amount, from, to);
        if (converted == null) {
            return Result.error(400, "不支持的币种或汇率暂不可用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("from", from.toUpperCase());
        data.put("to", to.toUpperCase());
        data.put("converted", converted);
        data.put("rate", exchangeRateService.getRateToCny(from));
        return Result.success(data);
    }

    /**
     * 手动触发汇率刷新（演示/运维用）
     */
    @PostMapping("/refresh")
    public Result<String> refresh() {
        int updated = exchangeRateService.refreshRates();
        return updated > 0
                ? Result.success("✅ 汇率刷新成功，更新 " + updated + " 个币种")
                : Result.error(500, "所有汇率源暂不可用，继续使用库内汇率");
    }
}
