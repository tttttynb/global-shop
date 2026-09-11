package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.PaymentCreateDto;
import com.bohao.globalshop.entity.PaymentOrder;
import com.bohao.globalshop.service.PaymentService;
import com.bohao.globalshop.vo.PaymentResultVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 支付控制器
 * <p>
 * 统一支付入口，支持多渠道支付创建、状态查询、渠道列表
 * 回调端点不对认证层拦截（在 WebConfig 中排除）
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付订单
     * <p>
     * 根据 channel 自动路由到余额/支付宝/微信/Stripe 支付网关
     */
    @PostMapping("/create")
    public Result<PaymentResultVo> createPayment(HttpServletRequest request,
                                                  @RequestBody PaymentCreateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return paymentService.createPayment(userId, dto);
    }

    /**
     * 查询支付订单状态（供前端轮询）
     * <p>
     * 第三方支付（支付宝/Stripe）需要前端轮询此接口确认支付结果
     */
    @GetMapping("/status/{paymentId}")
    public Result<PaymentOrder> queryStatus(HttpServletRequest request,
                                             @PathVariable Long paymentId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return paymentService.queryPaymentStatus(userId, paymentId);
    }

    /**
     * 获取当前启用的支付渠道列表
     * <p>
     * 前端根据此接口动态渲染支付方式选择面板
     */
    @GetMapping("/channels")
    public Result<List<Map<String, Object>>> getChannels() {
        return paymentService.getSupportedChannels();
    }

    /**
     * 支付回调统一入口（不需要用户认证）
     * <p>
     * 支付宝异步通知 / Stripe Webhook 均走此端点，
     * 根据 URL 中的 {channel} 路由到对应网关的 handleCallback
     */
    @PostMapping("/callback/{channel}")
    public Result<String> callback(@PathVariable String channel,
                                    @RequestParam Map<String, String> params) {
        return paymentService.handleCallback(channel, params);
    }
}
