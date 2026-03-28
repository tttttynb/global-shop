package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.vo.OrderVo;

import java.util.List;

public interface MerchantService {
    //申请开店
    Result<String> applyShop(Long userId, ShopApplyDto dto);

    //上架商品
    Result<String> publishProduct(Long userId, ProductPublishDto dto);

    // 获取商家自己店铺的订单列表 (数据隔离)
    Result<List<OrderVo>> getShopOrders(Long userId);

    // 商家操作发货
    Result<String> deliverOrder(Long userId, Long orderId);
}
