package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.dto.RefundApplyDto;
import com.bohao.globalshop.entity.RefundOrder;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.RefundService;
import com.bohao.globalshop.vo.OrderVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private RefundService refundService;

    @PostMapping("/create")
    public Result<String> createOrder(HttpServletRequest request, @RequestBody OrderCreateDto dto) {
        // 从拦截器放行时塞进来的数据里，拿到当前用户的 ID
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.createOrder(userId, dto);
    }

    @GetMapping("/my")
    public Result<List<OrderVo>> getMyOrders(HttpServletRequest request) {
        // 1. 从保安（拦截器）那里拿到当前是谁在查
        Long userId = (Long) request.getAttribute("currentUserId");
        // 2. 去业务层捞数据
        return orderService.getMyOrders(userId);
    }

    @PostMapping("/checkout")
    public Result<String> checkoutCart(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.checkoutCart(userId);
    }

    @PostMapping("/pay/{id}")
    public Result<String> payOrder(HttpServletRequest request, @PathVariable("id") Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.payOrder(userId, orderId);
    }

    @PostMapping("/confirm-receipt/{id}")
    public Result<String> confirmReceipt(HttpServletRequest request, @PathVariable("id") Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.confirmReceipt(userId, orderId);
    }

    // 提交商品评价接口
    @PostMapping("/review")
    public Result<String> submitReview(HttpServletRequest request, @RequestBody com.bohao.globalshop.dto.ReviewSubmitDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.submitReview(userId, dto);
    }

    @GetMapping("/{id}")
    public Result<OrderVo> getOrderDetail(HttpServletRequest request, @PathVariable("id") Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return orderService.getOrderDetail(userId, orderId);
    }

    @PostMapping("/refund/apply")
    public Result<String> applyRefund(HttpServletRequest request, @RequestBody RefundApplyDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.applyRefund(userId, dto);
    }

    @GetMapping("/refund/list")
    public Result<List<RefundOrder>> getMyRefunds(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.getMyRefunds(userId);
    }

    @GetMapping("/refund/{id}")
    public Result<RefundOrder> getRefundDetail(HttpServletRequest request, @PathVariable("id") Long refundId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.getRefundDetail(userId, refundId);
    }

    @PostMapping("/refund/{id}/cancel")
    public Result<String> cancelRefund(HttpServletRequest request, @PathVariable("id") Long refundId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return refundService.cancelRefund(userId, refundId);
    }
}
