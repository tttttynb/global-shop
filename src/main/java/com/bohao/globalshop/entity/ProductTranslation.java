package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品多语言译文（Phase 3 - F5 多语言）
 * <p>
 * 商品发布/编辑后通过 ProductPublishedEvent 事件驱动异步 AI 翻译（复用直播翻译链路），
 * 落库 en/ja/ko/th 四语。前台按 Accept-Language / lang 参数返回译文，无译文时回退中文原文。
 * </p>
 */
@Data
@TableName("product_translation")
public class ProductTranslation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    /** 语言代码: en/ja/ko/th */
    private String lang;
    /** 译文标题 */
    private String title;
    /** 译文描述 */
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
