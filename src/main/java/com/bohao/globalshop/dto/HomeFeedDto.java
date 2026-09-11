package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页个性化 Feed 响应 DTO（Tier 1.2）
 * <p>
 * 不同层级用户看到不同的分区和内容
 * </p>
 */
@Data
public class HomeFeedDto {

    /** 用户层级 */
    private String userTier;

    /** 个性化分区列表 */
    private List<HomeSection> sections;

    @Data
    public static class HomeSection {
        /** 分区标识（用于前端区分） */
        private String key;
        /** 分区标题（含 emoji） */
        private String title;
        /** 分区描述 */
        private String subtitle;
        /** 跳转链接 */
        private String link;
        /** 分区商品列表 */
        private List<ProductItem> products;

        @Data
        public static class ProductItem {
            private Long id;
            private String name;
            private BigDecimal price;
            private String coverImage;
            private String shopName;
            private Integer salesCount;
            private String tag; // 角标：新品/爆款/折扣
        }
    }
}
