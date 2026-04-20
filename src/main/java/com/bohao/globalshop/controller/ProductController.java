package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.repository.EsProductRepository;
import com.bohao.globalshop.service.ProductService;
import com.bohao.globalshop.service.impl.ProductServiceImpl;
import com.bohao.globalshop.vo.ProductVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;
    private final EsProductRepository esProductRepository;
    private final ProductMapper productMapper;


    @GetMapping("/list")
    public Result<List<ProductVo>> getList() {
        return productService.getProductListWithShop();
    }

    // 获取商品的评价列表
    @GetMapping("/{id}/reviews")
    public Result<List<com.bohao.globalshop.vo.ProductReviewVo>> getProductReviews(@PathVariable("id") Long productId) {
        return productService.getProductReviews(productId);
    }

    @GetMapping("/detail/{id}")
    public Result<Product> getProductDetail(@PathVariable("id") Long id) {
        // 直接呼叫 Service 层的神级缓存逻辑
        Product product = productService.getProductDetail(id);
        if (product == null) {
            return Result.error(404, "哎呀，商品找不到了！");
        }
        return Result.success(product);
    }

    /**
     *  将 MySQL 数据全量同步到 Elasticsearch
     */
    @GetMapping("/sync-es")
    public Result<String> syncToEs() {
        // 1. 从 MySQL 数据库里全量捞出所有上架的商品
        List<Product> mysqlProducts = productMapper.selectList(null);
        if (mysqlProducts == null || mysqlProducts.isEmpty()) {
            return Result.error(400, "MySQL 里没有商品，无需同步！");
        }
        ArrayList<EsProduct> esProductList = new ArrayList<>();

        //2.将MySQL的实体类，转换为ES的文档模型
        for (Product p : mysqlProducts) {
            EsProduct esDoc = new EsProduct();
            esDoc.setId(p.getId());
            esDoc.setName(p.getName());
            esDoc.setDescription(p.getDescription());
            esDoc.setPrice(p.getPrice());
            esDoc.setShopId(p.getShopId());
            esDoc.setCoverImage(p.getCoverImage());

            // 如果以后要接入 AI 向量模型，也是在这里把 p.getName() 发给大模型生成 float[] 塞进 esDoc！

            esProductList.add(esDoc);
        }
        // 3.一键批量保存到 Elasticsearch 索引库中！
        esProductRepository.saveAll(esProductList);
        return Result.success("太牛了！成功将 " + esProductList.size() + " 条商品数据同步至 ES！");
    }

    /**
     * C端全文智能检索接口 (底层走 ES 8.x + IK 分词)
     */
    @GetMapping("/search")
    public Result<List<EsProduct>> searchFromEs(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,// 默认第0页
            @RequestParam(value = "size", defaultValue = "10") int size) {// 默认每页10条
        // 1. 构造分页参数 (Spring Data 的页码是从 0 开始的)
        PageRequest pageRequest = PageRequest.of(page, size);
        // 2. 呼叫 ES Repository 进行全文检索！
        // 逻辑：只要 商品名称 或 商品描述 里匹配到了关键字，就会被秒搜出来！
        Page<EsProduct> searchResult = esProductRepository.findByNameOrDescription(keyword, keyword, pageRequest);
        // 3. 提取查到的数据列表返回给前端
        List<EsProduct> content = searchResult.getContent();

        return Result.success(content);
    }
}
