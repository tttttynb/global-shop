package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.service.CartService;
import com.bohao.globalshop.vo.CartItemVo;
import com.bohao.globalshop.vo.CartShopVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public Result<String> addToCart(HttpServletRequest request, @RequestBody CartAddDto dto) {
        // 从拦截器里获取当前用户身份
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.addToCart(userId, dto);
    }

    @GetMapping("/list")
    public Result<List<CartShopVo>> getCartList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.getCartList(userId);
    }

    @DeleteMapping("/remove/{id}")
    public Result<String> removeCartItem(HttpServletRequest request, @PathVariable("id") Long cartItemId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.removeCartItem(userId, cartItemId);
    }
}
