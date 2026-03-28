package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.service.ProductService;
import com.bohao.globalshop.service.impl.ProductServiceImpl;
import com.bohao.globalshop.vo.ProductVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;


    @GetMapping("/list")
    private Result<List<ProductVo>> getList() {
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

}
