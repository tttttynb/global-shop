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

    /**
     * 🆕 批量加购（Phase 4 - F10）：AI 购物顾问"一键全部加购"成套推荐
     */
    @PostMapping("/batch")
    public Result<String> batchAddToCart(HttpServletRequest request,
                                         @RequestBody com.bohao.globalshop.dto.BatchCartAddDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return Result.error(400, "加购清单为空");
        }
        int success = 0;
        for (CartAddDto item : dto.getItems()) {
            try {
                Result<String> r = cartService.addToCart(userId, item);
                if (r.getCode() == 200) {
                    success++;
                }
            } catch (Exception e) {
                // 单品失败不阻断整批
            }
        }
        if (success == 0) {
            return Result.error(400, "加购失败，请检查商品状态");
        }
        return Result.success("已将 " + success + " 件商品加入购物车");
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

    @PutMapping("/update/{id}")
    public Result<String> updateQuantity(HttpServletRequest request, @PathVariable("id") Long cartItemId, @RequestParam Integer quantity) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return cartService.updateQuantity(userId, cartItemId, quantity);
    }
}
