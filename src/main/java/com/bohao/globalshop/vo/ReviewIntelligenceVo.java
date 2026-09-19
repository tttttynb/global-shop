package com.bohao.globalshop.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI口碑档案（Phase 4 - F9）：买家印象标签墙 + 档案卡片
 */
@Data
public class ReviewIntelligenceVo {
    private Long productId;
    private String lang;
    /** 优点 Top3 */
    private List<String> pros;
    /** 缺点 Top3 */
    private List<String> cons;
    /** 适合人群 */
    private String bestFor;
    /** 综合推荐度 0-100 */
    private Integer recommendScore;
    /** 买家印象标签墙 */
    private List<ImpressionTag> impressions;
    /** 一句话总结 */
    private String summary;
    private Integer reviewCount;
    private LocalDateTime updateTime;
    /** true=当前语言的档案（false=回退中文展示） */
    private Boolean exactLang;

    @Data
    public static class ImpressionTag {
        private String tag;
        /** pos / neg */
        private String sentiment;
        private Integer count;
    }
}
