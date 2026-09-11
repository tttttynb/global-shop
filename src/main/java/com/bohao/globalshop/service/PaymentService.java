package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.PaymentCreateDto;
import com.bohao.globalshop.entity.PaymentOrder;
import com.bohao.globalshop.vo.PaymentResultVo;

import java.util.List;
import java.util.Map;

/**
 * 支付服务编排层
 * <p>
 * 负责根据支付渠道路由到对应的 PaymentGateway 实现，
 * 对外提供统一的支付 API
 */
public interface PaymentService {

    /**
     * 创建支付订单
     * <p>
     * 根据 dto.channel 自动路由到对应的支付网关：
     * - channel=0 → BalancePaymentGateway（余额支付，同步完成）
     * - channel=1 → AlipayPaymentGateway（支付宝，返回支付链接）
     * - channel=3 → StripePaymentGateway（Stripe，返回 clientSecret）
     *
     * @param userId 当前用户ID
     * @param dto    支付创建请求
     * @return 支付结果VO
     */
    Result<PaymentResultVo> createPayment(Long userId, PaymentCreateDto dto);

    /**
     * 处理支付回调（第三方支付渠道异步通知）
     *
     * @param channel 支付渠道名称（alipay/wechat/stripe）
     * @param params  回调参数
     * @return 处理结果
     */
    Result<String> handleCallback(String channel, Map<String, String> params);

    /**
     * 查询支付订单状态（供前端轮询）
     *
     * @param userId         当前用户ID
     * @param paymentOrderId 支付订单ID
     * @return 支付订单实体
     */
    Result<PaymentOrder> queryPaymentStatus(Long userId, Long paymentOrderId);

    /**
     * 获取当前支持的支付渠道列表
     *
     * @return 渠道信息列表
     */
    Result<List<Map<String, Object>>> getSupportedChannels();
}
