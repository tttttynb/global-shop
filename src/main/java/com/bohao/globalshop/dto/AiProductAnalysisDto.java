package com.bohao.globalshop.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiProductAnalysisDto {
    private String name;         // 极具吸引力的商品标题
    private String description;  // 包含表情包的五星级商品描述
    private List<String> tags;   // AI 自动生成的分类标签
}
