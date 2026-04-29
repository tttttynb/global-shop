package com.bohao.globalshop.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.List;

// 1. 指定 ES 中的索引名称（相当于 MySQL 的表名）
@Data
@Document(indexName = "product_index_v3")
public class EsProduct {
    @Id
    private Long id;// 对应 ES 的主键 _id
    // 2. 🚨 极其核心：配置中文分词器！
    // analyzer = "ik_max_word" 表示在存入时，把“波豪数码”切成“波豪”、“数码”等词
    // searchAnalyzer = "ik_smart" 表示搜索时，用最聪明的颗粒度去匹配
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
    @Field(type = FieldType.Double)
    private BigDecimal price;
    @Field(type = FieldType.Keyword, name = "shop_id")// Keyword 表示不分词，精确匹配（比如按店铺搜索）
    private Long shopId;
    @Field(type = FieldType.Keyword, name = "shop_name")
    private String shopName;
    @Field(type = FieldType.Keyword, name = "cover_image")
    private String coverImage;

    // 🚀 终极杀器：用来存储 AI 大模型生成的 1024 维度的语义向量
    // dims = 1024 (对应 text-embedding-v3 的输出维度)
    // index = true (告诉 ES 对这个浮点数组建立 KNN 向量索引)
    // 极其关键：加上 @JsonIgnore，禁止把这 1024 个数字传给前端！
    @JsonIgnore
    @Field(type = FieldType.Dense_Vector, dims = 1024, index = true)
    private List<Float> vector;
}
