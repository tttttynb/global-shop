package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品发布/更新时前端提交的 SKU 项
 */
@Data
public class SkuDto {
    /** 已有 SKU 的 ID：编辑场景回传时按 ID 原地更新，保持 SKU ID 不变（购物车/订单快照引用不失效）；为空则新增 */
    private Long id;
    /** 规格 JSON 字符串，如 {"颜色":"红","尺码":"M"}；单规格可为空 */
    private String specJson;
    /** 规格展示文本，如 "红 / M"；单规格可为空 */
    private String specText;
    private BigDecimal price;
    private Integer stock;
    /** SKU 专属图片（可空） */
    private String image;
}
