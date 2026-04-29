package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LiveRoomVo {
    private Long id;
    private Long shopId;
    private String shopName;
    private Long userId;
    private String title;
    private String coverImage;
    private Integer status;
    private String pushUrl;
    private String pullUrl;
    private Integer viewerCount;
    private Boolean aiAssistantEnabled;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<LiveProductVo> products;

    @Data
    public static class LiveProductVo {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private BigDecimal price;
        private Integer stock;
        private Integer sortOrder;
        private Boolean isExplaining;
    }
}
