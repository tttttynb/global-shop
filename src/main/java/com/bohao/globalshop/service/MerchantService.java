package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.vo.OrderVo;

import java.util.List;
import java.util.Map;

public interface MerchantService {
    Result<String> applyShop(Long userId, ShopApplyDto dto);

    Result<String> publishProduct(Long userId, ProductPublishDto dto);

    Result<List<OrderVo>> getShopOrders(Long userId);

    Result<String> deliverOrder(Long userId, Long orderId);

    Result<List<Product>> getMerchantProducts(Long userId);

    Result<String> updateProduct(Long userId, Long productId, ProductPublishDto dto);

    Result<String> toggleProductStatus(Long userId, Long productId, Integer status);

    Result<String> deleteProduct(Long userId, Long productId);

    Result<Shop> getShopInfo(Long userId);

    Result<String> updateShopInfo(Long userId, ShopApplyDto dto);

    Result<Map<String, Object>> getDashboard(Long userId);
}
