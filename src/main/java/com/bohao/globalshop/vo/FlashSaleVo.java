package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动展示 VO（主播控制台 + 观众端卡片共用）
 */
@Data
public class FlashSaleVo {
    private Long id;
    private Long liveRoomId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImage;
    private String skuSpec;
    private BigDecimal flashPrice;
    private BigDecimal originalPrice;
    private Integer totalQty;
    private Integer remainQty;
    /** 已售进度百分比 0-100 */
    private Integer soldPercent;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0=进行中 1=已结束 2=已取消 */
    private Integer status;
}
