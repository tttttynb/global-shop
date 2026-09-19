package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI口碑档案（Phase 4 - F9）：持久化 + 多语言（uk: product_id + lang）
 * <p>
 * content_json 结构：
 * {"pros":[".."],"cons":[".."],"bestFor":"..","recommendScore":92,
 *  "impressions":[{"tag":"物流快","sentiment":"pos","count":5}],"summary":".."}
 */
@Data
@TableName("review_intelligence")
public class ReviewIntelligence {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 语言（zh/en/ja/ko/th） */
    private String lang;

    /** 结构化档案JSON */
    private String contentJson;

    /** 生成时评价数 */
    private Integer reviewCount;

    /** 生成模型版本 */
    private String modelVersion;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
