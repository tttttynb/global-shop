package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.vo.ProductReviewVo;
import com.bohao.globalshop.vo.ProductVo;

import java.util.List;
import java.util.Map;

public interface ProductService {

    Result<List<ProductVo>> getProductListWithShop();

    Result<Map<String, Object>> getProductListPaged(Long categoryId, String sort, Integer page, Integer size);

    Result<List<ProductReviewVo>> getProductReviews(Long productId);

    Product getProductDetail(Long id);

    Result<String> toggleFavorite(Long userId, Long productId);

    Result<List<ProductVo>> getFavorites(Long userId);
}
