package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.vo.ProductReviewVo;
import com.bohao.globalshop.vo.ProductVo;

import java.util.List;

public interface ProductService {
//    Result<List<Product>> getProductList();

    Result<List<ProductVo>> getProductListWithShop();

    Result<List<ProductReviewVo>> getProductReviews(Long productId);

    Product getProductDetail(Long id);
}
