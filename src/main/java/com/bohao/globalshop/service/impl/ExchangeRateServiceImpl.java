package com.bohao.globalshop.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.ExchangeRate;
import com.bohao.globalshop.mapper.ExchangeRateMapper;
import com.bohao.globalshop.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ExchangeRateMapper exchangeRateMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 缓存 key 前缀：forex:rate:{CODE} */
    private static final String RATE_CACHE_KEY = "forex:rate:";

    /** 汇率 API 多源列表（按顺序尝试，前者失败自动切后者） */
    private static final String[] RATE_API_SOURCES = {
            // 源1：open.er-api.com 免费汇率（1 CNY = X 外币）
            "https://open.er-api.com/v6/latest/CNY",
            // 源2：欧洲央行参考汇率 frankfurter（1 CNY = X 外币）
            "https://api.frankfurter.dev/v1/latest?base=CNY"
    };
    private static final String[] SOURCE_NAMES = {"er-api", "frankfurter"};

    /** 币种元数据：代码 → [中文名, 符号]（LinkedHashMap 保证切换器展示顺序稳定） */
    private static final Map<String, String[]> CURRENCY_META = new LinkedHashMap<>();
    static {
        CURRENCY_META.put("USD", new String[]{"美元", "$"});
        CURRENCY_META.put("EUR", new String[]{"欧元", "€"});
        CURRENCY_META.put("GBP", new String[]{"英镑", "£"});
        CURRENCY_META.put("JPY", new String[]{"日元", "JP¥"});
        CURRENCY_META.put("KRW", new String[]{"韩元", "₩"});
        CURRENCY_META.put("THB", new String[]{"泰铢", "฿"});
    }

    /** 汇率总开关（关闭后仅使用库内/兜底汇率，不外呼 API） */
    @Value("${app.forex.enabled:true}")
    private boolean forexEnabled;

    /** Redis 缓存时长（分钟），默认 60 */
    @Value("${app.forex.cache-minutes:60}")
    private long cacheMinutes;

    /** 静态兜底汇率：USD=7.10,EUR=7.80,... （API 全挂且库为空时保命用） */
    @Value("${app.forex.fallback-rates:USD=7.10,EUR=7.80,GBP=9.05,JPY=0.048,KRW=0.0052,THB=0.21}")
    private String fallbackRatesConfig;

    // ==================== 查询 ====================

    @Override
    public BigDecimal getRateToCny(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank() || "CNY".equalsIgnoreCase(currencyCode)) {
            return BigDecimal.ONE;
        }
        String code = currencyCode.toUpperCase();

        // 1. Redis 缓存
        try {
            String cached = stringRedisTemplate.opsForValue().get(RATE_CACHE_KEY + code);
            if (cached != null) {
                return new BigDecimal(cached);
            }
        } catch (Exception e) {
            log.warn("读取汇率缓存失败，降级查库: {}", e.getMessage());
        }

        // 2. MySQL
        ExchangeRate rate = selectByCode(code);
        if (rate != null && rate.getRateToCny() != null) {
            cacheRate(code, rate.getRateToCny());
            return rate.getRateToCny();
        }

        // 3. 配置兜底
        BigDecimal fallback = parseFallbackRates().get(code);
        if (fallback != null) {
            log.warn("币种 {} 库内无汇率，使用配置兜底值 {}", code, fallback);
            cacheRate(code, fallback);
            return fallback;
        }
        return null;
    }

    @Override
    public List<ExchangeRate> getAllRates() {
        List<ExchangeRate> rates = new ArrayList<>();
        // CNY 基准行（不落库，展示用）
        ExchangeRate cny = new ExchangeRate();
        cny.setCurrencyCode("CNY");
        cny.setCurrencyName("人民币");
        cny.setSymbol("¥");
        cny.setRateToCny(BigDecimal.ONE);
        cny.setSource("base");
        cny.setUpdateTime(LocalDateTime.now());
        rates.add(cny);

        List<ExchangeRate> dbRates = exchangeRateMapper.selectList(
                new QueryWrapper<ExchangeRate>().orderByAsc("id"));
        if (dbRates != null && !dbRates.isEmpty()) {
            rates.addAll(dbRates);
            return rates;
        }
        // 库为空（迁移脚本未执行的极端情况）：用配置兜底拼一份，保证前端切换器可用
        Map<String, BigDecimal> fallback = parseFallbackRates();
        for (Map.Entry<String, String[]> meta : CURRENCY_META.entrySet()) {
            BigDecimal fr = fallback.get(meta.getKey());
            if (fr == null) continue;
            ExchangeRate er = new ExchangeRate();
            er.setCurrencyCode(meta.getKey());
            er.setCurrencyName(meta.getValue()[0]);
            er.setSymbol(meta.getValue()[1]);
            er.setRateToCny(fr);
            er.setSource("fallback");
            er.setUpdateTime(LocalDateTime.now());
            rates.add(er);
        }
        return rates;
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null) return null;
        String f = from == null ? "CNY" : from.toUpperCase();
        String t = to == null ? "CNY" : to.toUpperCase();
        if (f.equals(t)) return amount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal fromRate = getRateToCny(f); // 1 from = fromRate CNY
        BigDecimal toRate = getRateToCny(t);     // 1 to = toRate CNY
        if (fromRate == null || toRate == null || toRate.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // amount(from) → CNY → to
        return amount.multiply(fromRate).divide(toRate, 2, RoundingMode.HALF_UP);
    }

    @Override
    public List<String> getSupportedCurrencies() {
        return new ArrayList<>(CURRENCY_META.keySet());
    }

    // ==================== 刷新（定时任务调用） ====================

    @Override
    public int refreshRates() {
        if (!forexEnabled) {
            log.info("汇率刷新已关闭（app.forex.enabled=false）");
            return 0;
        }

        // 多源依次尝试：任一源拿到合法数据即成功
        Map<String, BigDecimal> fresh = null;
        String sourceName = null;
        for (int i = 0; i < RATE_API_SOURCES.length; i++) {
            try {
                fresh = fetchFromApi(RATE_API_SOURCES[i]);
                if (fresh != null && !fresh.isEmpty()) {
                    sourceName = SOURCE_NAMES[i];
                    break;
                }
            } catch (Exception e) {
                log.warn("汇率源 [{}] 拉取失败: {}", RATE_API_SOURCES[i], e.getMessage());
            }
        }

        if (fresh == null || fresh.isEmpty()) {
            // 全部失败：保留库内旧汇率（旧数据 > 无数据），仅刷新缓存供读取
            log.error("❌ 所有汇率源均不可用，保留库内旧汇率");
            for (String code : CURRENCY_META.keySet()) {
                ExchangeRate old = selectByCode(code);
                if (old != null) {
                    cacheRate(code, old.getRateToCny());
                }
            }
            return 0;
        }

        // 落库（upsert）+ 写缓存
        int updated = 0;
        for (Map.Entry<String, BigDecimal> entry : fresh.entrySet()) {
            String code = entry.getKey();
            String[] meta = CURRENCY_META.get(code);
            if (meta == null) continue;

            ExchangeRate rate = selectByCode(code);
            if (rate == null) {
                rate = new ExchangeRate();
                rate.setCurrencyCode(code);
            }
            rate.setCurrencyName(meta[0]);
            rate.setSymbol(meta[1]);
            rate.setRateToCny(entry.getValue());
            rate.setSource(sourceName);
            rate.setUpdateTime(LocalDateTime.now());
            if (rate.getId() == null) {
                exchangeRateMapper.insert(rate);
            } else {
                exchangeRateMapper.updateById(rate);
            }
            cacheRate(code, entry.getValue());
            updated++;
        }
        log.info("✅ 汇率刷新成功（来源: {}）：{} 个币种 {}", sourceName, updated, fresh);
        return updated;
    }

    /**
     * 调用免费汇率 API。两个源的返回结构一致：{"rates":{"USD":0.14,...}}（1 CNY = X 外币）
     * 转换为 rateToCny = 1 / X，并做合法性校验（防脏数据污染结算）。
     */
    private Map<String, BigDecimal> fetchFromApi(String url) {
        String body = HttpUtil.get(url, 5000);
        if (body == null || !JSONUtil.isTypeJSONObject(body)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(body);
        // er-api 有 result 字段，失败时为 "error"
        if (json.containsKey("result") && !"success".equals(json.getStr("result"))) {
            return null;
        }
        JSONObject rates = json.getJSONObject("rates");
        if (rates == null) {
            return null;
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String code : CURRENCY_META.keySet()) {
            BigDecimal perCny = rates.getBigDecimal(code); // 1 CNY = perCny 外币
            if (perCny == null || perCny.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal rateToCny = BigDecimal.ONE.divide(perCny, 6, RoundingMode.HALF_UP);
            // 合理性校验：1 外币兑人民币应在 (0.0001, 10000) 区间，否则视为脏数据
            if (rateToCny.compareTo(new BigDecimal("0.0001")) > 0
                    && rateToCny.compareTo(new BigDecimal("10000")) < 0) {
                result.put(code, rateToCny);
            }
        }
        return result;
    }

    // ==================== 内部工具 ====================

    private ExchangeRate selectByCode(String code) {
        return exchangeRateMapper.selectOne(
                new QueryWrapper<ExchangeRate>().eq("currency_code", code));
    }

    private void cacheRate(String code, BigDecimal rate) {
        if (rate == null) return;
        try {
            stringRedisTemplate.opsForValue().set(RATE_CACHE_KEY + code,
                    rate.toPlainString(), cacheMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入汇率缓存失败: {}", e.getMessage());
        }
    }

    /** 解析配置兜底汇率 "USD=7.10,EUR=7.80,..." */
    private Map<String, BigDecimal> parseFallbackRates() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (fallbackRatesConfig == null || fallbackRatesConfig.isBlank()) return map;
        for (String pair : fallbackRatesConfig.split(",")) {
            String[] kv = pair.trim().split("=");
            if (kv.length == 2) {
                try {
                    map.put(kv[0].trim().toUpperCase(), new BigDecimal(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }
}
