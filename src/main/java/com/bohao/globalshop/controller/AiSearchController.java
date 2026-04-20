package com.bohao.globalshop.controller;

import co.elastic.clients.elasticsearch._types.KnnQuery;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.repository.EsProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiSearchController {
    // 1. 注入 Spring AI 的核心大模型客户端
    private final EmbeddingModel embeddingModel;
    //2. 注入基础的 ES 增删改查接口
    private final EsProductRepository esProductRepository;
    //3. 注入 Spring Data ES 的高级操作模板 (用于执行复杂的 KNN 向量检索)
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 接口一：【AI 灵魂注入机】
     * 作用：把数据库里的老商品，全部掏出来喂给大模型，生成向量后再存回 ES
     */
    @GetMapping("/init-vectors")
    public String initVectors() {
        Iterable<EsProduct> products = esProductRepository.findAll();
        int count = 0;
        for (EsProduct product : products) {
            // 将商品的核心信息，拼接成一段连贯的“自然语言”
            String textToEmbed = "商品名称：" + product.getName() + "。商品描述：" + product.getDescription();
            //呼叫大模型，瞬间降维成 1024 维的浮点数组！
            float[] vectors = embeddingModel.embed(textToEmbed);
            // 将“灵魂向量”塞回商品，并重新存入 ES
            product.setVector(vectors);
            esProductRepository.save(product);

            count++;
            System.out.println("成功注入向量 -> " + product.getName());
        }
        return "太强了！成功为 " + count + " 个商品注入了 AI 语义向量！";
    }

    /**
     * 接口二：【大模型语义导购】
     * 作用：不用精准匹配关键字，直接根据买家的“大白话”去猜测意图并推荐商品！
     */
    @GetMapping("/semantic")
    public List<EsProduct> semanticSearch(@RequestParam String keyWord) {
        System.out.println("买家搜索原话：" + keyWord);
        // 1. 🧠 意图识别：将买家的大白话，也转为 1024 维度的意图向量
        float[] userVector = embeddingModel.embed(keyWord);
        // （类型转换：ES 8.x 底层 API 需要 List<Float> 格式）
        List<Float> vectorList = new ArrayList<>();
        for (float v : userVector) {
            vectorList.add(v);
        }
        // 2.空间跃迁：构建 KNN (K近邻) 向量查询
        KnnQuery knnQuery = KnnQuery.of(k -> k
                .field("vector")    // 在哪个字段里找？
                .queryVector(vectorList)  // 传入买家的意图向量
                .k(5)               // 返回最相似的 5 件商品
                .numCandidates(50)  // 底层算法先在 50 个候选人里粗筛，保证极速
        );
        NativeQuery query = NativeQuery.builder()
                .withKnnQuery(knnQuery)
                .build();
        // 3.降维打击：执行毫秒级多维空间检索！
        SearchHits<EsProduct> hits = elasticsearchOperations.search(query, EsProduct.class);
        // 4. 解析战果并返回
        List<EsProduct> result = new ArrayList<>();
        for (SearchHit<EsProduct> hit : hits) {
            EsProduct product = hit.getContent();
            // 极其硬核：在控制台打印出 AI 算出来的“语义相似度分数”！
            System.out.println("🎯 命中商品: [" + product.getName() + "]，AI 相似度得分: " + hit.getScore());
            result.add(product);
        }

        return result;

    }
}
