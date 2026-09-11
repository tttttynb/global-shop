package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.vo.ImageSearchResultVo;

/**
 * AI 以图搜图服务（Phase 2 - F4）
 * <p>
 * V1 路径：图片 → qwen-vl 视觉理解（结构化 JSON）→ 拼装语义查询文本
 * → text-embedding-v4 向量化 → ES kNN 召回 → 个性化重排。
 * 全部复用现有链路（visionModel Bean / EmbeddingModel / EsProductRepository）。
 * </p>
 */
public interface ImageSearchService {

    /**
     * @param imageBytes  图片字节
     * @param mimeType    图片类型（image/jpeg、image/png、image/webp）
     */
    Result<ImageSearchResultVo> searchByImage(byte[] imageBytes, String mimeType);
}
