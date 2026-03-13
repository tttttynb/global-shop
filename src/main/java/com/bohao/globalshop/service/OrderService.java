package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.vo.OrderVo;

import java.util.List;

public interface OrderService {
    Result<String> createOrder(Long userId, OrderCreateDto dto);

    // 获取当前用户的订单列表
    Result<List<OrderVo>> getMyOrders(Long userId);

    // 支付订单
    Result<String> payOrder(Long userId, Long orderId);

    //购物车一件结算
    Result<String> checkoutCart(Long userId);

    //取消订单
    Result<String> cancelSingleOrder(Long orderId);
}
