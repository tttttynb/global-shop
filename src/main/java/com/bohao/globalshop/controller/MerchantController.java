package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CouponCreateDto;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.entity.Coupon;
import com.bohao.globalshop.entity.RefundOrder;
import com.bohao.globalshop.service.CouponService;
import com.bohao.globalshop.service.MerchantService;
import com.bohao.globalshop.service.RefundService;
import com.bohao.globalshop.vo.OrderVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;
    private final RefundService refundService;
    private final CouponService couponService;

    @PostMapping("/shop/apply")
    public Result<String> applyShop(HttpServletRequest request, @RequestBody ShopApplyDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.applyShop(userId, dto);
    }

    @PostMapping("/product/publish")
    public Result<String> publishProduct(HttpServletRequest request, @RequestBody ProductPublishDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.publishProduct(userId, dto);
    }

    @GetMapping("/product/list")
    public Result<?> getMerchantProducts(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.getMerchantProducts(userId);
    }

    @PutMapping("/product/{id}")
    public Result<String> updateProduct(HttpServletRequest request, @PathVariable("id") Long productId, @RequestBody ProductPublishDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.updateProduct(userId, productId, dto);
    }

    @PostMapping("/product/{id}/status")
    public Result<String> toggleProductStatus(HttpServletRequest request, @PathVariable("id") Long productId, @RequestParam Integer status) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.toggleProductStatus(userId, productId, status);
    }

    @DeleteMapping("/product/{id}")
    public Result<String> deleteProduct(HttpServletRequest request, @PathVariable("id") Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.deleteProduct(userId, productId);
    }

    @GetMapping("/order/list")
    public Result<List<OrderVo>> getShopOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.getShopOrders(userId);
    }

    @PostMapping("/order/deliver/{id}")
    public Result<String> deliverOrder(HttpServletRequest request, @PathVariable("id") Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.deliverOrder(userId, orderId);
    }

    @GetMapping("/shop/info")
    public Result<?> getShopInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.getShopInfo(userId);
    }

    @PutMapping("/shop/info")
    public Result<String> updateShopInfo(HttpServletRequest request, @RequestBody ShopApplyDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.updateShopInfo(userId, dto);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.getDashboard(userId);
    }

    // 退款管理
    @GetMapping("/refund/list")
    public Result<List<RefundOrder>> getShopRefunds(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.getShopRefunds(userId);
    }

    @PostMapping("/refund/{id}/approve")
    public Result<String> approveRefund(HttpServletRequest request, @PathVariable("id") Long refundId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.approveRefund(userId, refundId);
    }

    @PostMapping("/refund/{id}/reject")
    public Result<String> rejectRefund(HttpServletRequest request, @PathVariable("id") Long refundId, @RequestParam String reason) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.rejectRefund(userId, refundId, reason);
    }

    // 优惠券管理
    @PostMapping("/coupon/create")
    public Result<String> createCoupon(HttpServletRequest request, @RequestBody CouponCreateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return couponService.createCoupon(userId, dto);
    }

    @GetMapping("/coupon/list")
    public Result<List<Coupon>> getMerchantCoupons(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return couponService.getMerchantCoupons(userId);
    }
}
