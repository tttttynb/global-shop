package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.vo.CartItemVo;

import java.util.List;


public interface CartService {
    Result<String> addToCart(Long userId, CartAddDto dto);

    Result<List<CartItemVo>> getMyCart(Long userId);

    // 将商品移出购物车
    Result<String> removeCartItem(Long userId, Long cartItemId);
}
