package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<String> createOrder(HttpServletRequest request, @RequestBody OrderCreateDto dto) {
        // 从拦截器放行时塞进来的数据里，拿到当前用户的 ID
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.createOrder(userId, dto);
    }
}
