package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.ProductTranslation;

import java.util.List;

/**
 * 商品多语言翻译服务（Phase 3 - F5 多语言）
 * <p>
 * 复用直播翻译链路（TranslationService → 通义千问），商品发布/编辑后事件驱动异步翻译，
 * 译文持久化到 product_translation 表；前台按语言返回译文，无译文回退中文原文。
 * </p>
 */
public interface ProductTranslationService {

    /**
     * 异步翻译商品标题+描述到全部目标语言（en/ja/ko/th），已存在则覆盖更新
     */
    void translateProductAsync(Long productId);

    /**
     * 查询某语言译文；lang 为空/zh 或无译文时返回 null（调用方回退原文）
     */
    ProductTranslation getTranslation(Long productId, String lang);

    /**
     * 商品全部译文列表
     */
    List<ProductTranslation> listTranslations(Long productId);

    /**
     * 商品删除时级联清理译文
     */
    void removeByProductId(Long productId);

    /**
     * 翻译目标语言列表（配置 app.i18n.target-languages）
     */
    List<String> getTargetLanguages();
}
