package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.service.CartService;
import com.bohao.globalshop.vo.CartItemVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    @Autowired
    private CartService cartService;

    @PostMapping("/add")
    public Result<String> addToCart(HttpServletRequest request, @RequestBody CartAddDto dto) {
        // 从拦截器里获取当前用户身份
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.addToCart(userId, dto);
    }

    @GetMapping("/list")
    public Result<List<CartItemVo>> getMyCart(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.getMyCart(userId);
    }

    @DeleteMapping("/remove/{id}")
    public Result<String> removeCartItem(HttpServletRequest request, @PathVariable("id") Long cartItemId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.removeCartItem(userId, cartItemId);
    }
}
