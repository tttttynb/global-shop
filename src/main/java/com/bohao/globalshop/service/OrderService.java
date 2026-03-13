package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;

public interface OrderService {
    Result<String> createOrder(Long userId, OrderCreateDto dto);
}
