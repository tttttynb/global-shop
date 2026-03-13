package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;

import java.util.List;

public interface ProductService {
    Result<List<Product>> getProductList();
}
