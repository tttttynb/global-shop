package com.bohao.globalshop.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 评价总结 DTO（Tier 1.1）
 * <p>
 * LLM 聚合商品所有评论后生成的结构化总结
 * </p>
 */
@Data
public class AiReviewSummaryDto {

    /** 优点列表 */
    private List<String> pros;

    /** 缺点列表 */
    private List<String> cons;

    /** 适合人群 */
    private String bestFor;

    /** AI 综合评分（0-5） */
    private Double aiRating;

    /** 总结短文（100字内） */
    private String summary;

    /** 基于评论数 */
    private Integer reviewCount;
}
