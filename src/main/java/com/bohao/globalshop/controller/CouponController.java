package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.service.CouponService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @PostMapping("/claim/{id}")
    public Result<String> claimCoupon(HttpServletRequest request, @PathVariable("id") Long couponId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return couponService.claimCoupon(userId, couponId);
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> getMyCoupons(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return couponService.getMyCoupons(userId);
    }

    @GetMapping("/available")
    public Result<List<Map<String, Object>>> getAvailableCoupons(
            HttpServletRequest request,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) BigDecimal amount) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return couponService.getAvailableCoupons(userId, shopId, amount);
    }
}
