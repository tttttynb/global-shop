package com.bohao.globalshop.service;

import com.bohao.globalshop.vo.ReviewIntelligenceVo;

/**
 * AI口碑档案 2.0（Phase 4 - F9）
 * <p>
 * 持久化 review_intelligence（优点Top3/缺点Top3/适合人群/综合推荐度/买家印象标签墙，结构化 JSON）；
 * 新评价增量触发重算（Redis pending 集合防抖合并 + 定时刷新）；
 * 多语言版本复用 F5 翻译链路（TranslationService → en/ja/ko/th）。
 */
public interface ReviewIntelligenceService {

    /**
     * 查询商品口碑档案：命中该语言直接返回；无该语言回退中文并异步补翻译；
     * 连中文都没有且评价数达标 → 现场同步生成一次。评价不足返回 null。
     */
    ReviewIntelligenceVo getForProduct(Long productId, String lang);

    /** 新评价产生 → 标记待重算（防抖：只记集合，由定时任务合并刷新） */
    void markPending(Long productId);

    /** 中文档案 → 多语言档案异步翻译（复用 F5 翻译链路，单语言失败不影响其他） */
    void translateAsync(Long productId);

    /** 定时刷新：批量重算 pending 商品，返回处理数量 */
    int flushPending();
}
