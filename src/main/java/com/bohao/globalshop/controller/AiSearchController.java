package com.bohao.globalshop.controller;

import co.elastic.clients.elasticsearch._types.KnnQuery;
import com.bohao.globalshop.common.Result;
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
    public Result<String> initVectors() {
        try {
            Iterable<EsProduct> products = esProductRepository.findAll();
            int count = 0;
            for (EsProduct product : products) {
                String textToEmbed = "商品名称：" + product.getName() + "。商品描述：" + product.getDescription();
                float[] vectors = embeddingModel.embed(textToEmbed);
                List<Float> vectorList = new ArrayList<>();
                for (float v : vectors) {
                    vectorList.add(v);
                }
                product.setVector(vectorList);
                esProductRepository.save(product);

                count++;
                System.out.println("成功注入向量 -> " + product.getName());
            }
            return Result.success("太强了！成功为 " + count + " 个商品注入了 AI 语义向量！");
        } catch (Exception e) {
            System.err.println("向量注入失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error(500, "向量注入失败: " + e.getMessage());
        }
    }

    @GetMapping("/semantic")
    public Result<List<EsProduct>> semanticSearch(@RequestParam String keyWord) {
        try {
            System.out.println("买家搜索原话：" + keyWord);
            float[] userVector = embeddingModel.embed(keyWord);
            List<Float> vectorList = new ArrayList<>();
            for (float v : userVector) {
                vectorList.add(v);
            }

            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.knn(k -> k
                            .field("vector")
                            .queryVector(vectorList)
                            .numCandidates(50)
                    ))
                     .withMaxResults(10)
                    .build();

            SearchHits<EsProduct> hits = elasticsearchOperations.search(query, EsProduct.class);
            List<EsProduct> result = new ArrayList<>();
            for (SearchHit<EsProduct> hit : hits) {
                EsProduct product = hit.getContent();
                System.out.println("🎯 命中商品: [" + product.getName() + "]，AI 相似度得分: " + hit.getScore());
                result.add(product);
            }

            return Result.success(result);
        } catch (Exception e) {
            System.err.println("语义搜索失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error(500, "搜索失败: " + e.getMessage());
        }
    }
}
