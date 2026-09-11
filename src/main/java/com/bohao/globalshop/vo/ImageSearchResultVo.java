package com.bohao.globalshop.vo;

import com.bohao.globalshop.entity.EsProduct;
import lombok.Data;

import java.util.List;

/**
 * AI 以图搜图结果（Phase 2 - F4）
 * <p>
 * recognized：视觉大模型对图片的结构化理解；
 * queryText：由理解结果拼装、实际用于向量召回的语义查询文本；
 * products：ES 向量召回 + 个性化重排后的商品列表。
 * </p>
 */
@Data
public class ImageSearchResultVo {
    /** AI 识别出的商品品类，如 "连衣裙" */
    private String category;
    /** AI 识别出的关键特征，如 ["红色", "碎花", "雪纺"] */
    private List<String> keywords;
    /** AI 生成的一句话商品描述 */
    private String description;
    /** 实际用于语义召回的查询文本 */
    private String queryText;
    /** 召回的商品列表 */
    private List<EsProduct> products;
}
