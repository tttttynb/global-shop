package com.bohao.globalshop.service.payment;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.enums.PaymentChannel;
import com.bohao.globalshop.enums.PaymentStatus;
import com.bohao.globalshop.vo.PaymentResultVo;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付网关策略接口
 * <p>
 * 所有支付渠道必须实现此接口。新增支付渠道只需添加新的实现类，
 * Spring 会自动注入到 PaymentService 中。
 * <p>
 * 示例：新增 PayPal 支付：
 * <pre>{@code
 * @Component
 * public class PayPalPaymentGateway implements PaymentGateway {
 *     public PaymentChannel getChannel() { return PaymentChannel.PAYPAL; }
 *     // ... 实现其他方法
 * }
 * }</pre>
 */
public interface PaymentGateway {

    /**
     * 返回该网关对应的支付渠道标识
     * <p>
     * PaymentService 通过此方法自动构建 channel → gateway 的路由表
     */
    PaymentChannel getChannel();

    /**
     * 创建支付订单
     * <p>
     * 内部需完成：
     * 1. 校验订单归属和状态
     * 2. 创建 PaymentOrder 记录
     * 3. 对于同步支付（如余额）：直接扣款并返回成功
     * 4. 对于异步支付（如支付宝）：返回支付参数给前端
     *
     * @param userId 当前用户ID
     * @param order  订单实体（调用前已查询）
     * @return 支付结果VO（各渠道返回内容不同）
     */
    Result<PaymentResultVo> createPayment(Long userId, TradeOrder order);

    /**
     * 处理第三方支付回调
     * <p>
     * 支付宝异步通知 / 微信支付回调 / Stripe webhook 统一走这个方法
     *
     * @param params 回调参数（GET/POST 参数转 Map）
     * @return 支付状态
     */
    PaymentStatus handleCallback(Map<String, String> params);

    /**
     * 主动查询支付订单状态
     * <p>
     * 用于：前端轮询 / 用户刷新支付页 / 对账
     */
    PaymentStatus queryStatus(Long paymentOrderId);

    /**
     * 退款
     * <p>
     * 余额支付：直接加回余额
     * 第三方支付：调用网关退款接口
     *
     * @param paymentOrderId 支付订单ID
     * @param amount         退款金额
     */
    Result<String> refund(Long paymentOrderId, BigDecimal amount);
}
