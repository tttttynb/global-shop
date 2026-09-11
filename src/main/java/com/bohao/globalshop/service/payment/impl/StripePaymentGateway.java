package com.bohao.globalshop.service.payment.impl;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.PaymentOrder;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.enums.NotificationTargetType;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.enums.PaymentChannel;
import com.bohao.globalshop.enums.PaymentStatus;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.PaymentOrderMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.payment.PaymentGateway;
import com.bohao.globalshop.vo.PaymentResultVo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Stripe 支付网关（占位实现）
 * <p>
 * 当前为 mock 模式，返回模拟的 clientSecret 供前端 Stripe.js 使用。
 * 接入真实 Stripe 时，将以下配置加入 application.yml 并置 enabled=true：
 * <pre>
 * app.payment.stripe:
 *   enabled: true
 *   secret-key: sk_live_xxx
 *   webhook-secret: whsec_xxx
 * </pre>
 * <p>
 * 真实接入步骤：
 * 1. 添加 stripe-java 依赖
 * 2. 用 Stripe API 创建 PaymentIntent，返回 clientSecret
 * 3. 前端用 @stripe/stripe-js 的 stripe.confirmPayment() 完成支付
 * 4. 实现 handleCallback() 验证 Stripe webhook 签名
 * 5. 实现 refund() 调用 Stripe Refund API
 * <p>
 * Stripe 特别适合跨境场景，支持全球 135+ 种货币和主流信用卡/电子钱包。
 */
@Slf4j
@Component("stripePaymentGateway")
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final PaymentOrderMapper paymentOrderMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        log.info("================================================");
        log.info("  🌍 Stripe 支付模块已加载（占位/Mock 模式）");
        log.info("  如需启用真实支付，请在 application.yml 中配置:");
        log.info("  app.payment.stripe.enabled=true");
        log.info("  app.payment.stripe.secret-key=sk_live_xxx");
        log.info("  app.payment.stripe.webhook-secret=whsec_xxx");
        log.info("================================================");
    }

    @Override
    public PaymentChannel getChannel() {
        return PaymentChannel.STRIPE;
    }

    @Override
    public Result<PaymentResultVo> createPayment(Long userId, TradeOrder order) {
        // 1. 安全校验
        if (!order.getUserId().equals(userId)) {
            return Result.error(403, "非法请求！这并非您的订单！");
        }
        if (order.getStatus() != 0) {
            if (order.getStatus() == 1) {
                return Result.error(400, "该订单已支付，请勿重复付款！");
            } else if (order.getStatus() == 2) {
                return Result.error(400, "该订单已超时自动取消，请重新下单！");
            } else {
                return Result.error(400, "该订单当前状态异常，无法支付。");
            }
        }

        // 2. 生成商户订单号
        String outTradeNo = "GSST" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 3. 创建待支付记录
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setUserId(userId);
        paymentOrder.setChannel(PaymentChannel.STRIPE.getCode());
        paymentOrder.setChannelName(PaymentChannel.STRIPE.getLabel());
        paymentOrder.setAmount(order.getTotalAmount());
        paymentOrder.setStatus(PaymentStatus.PENDING.getCode());
        paymentOrder.setOutTradeNo(outTradeNo);
        paymentOrderMapper.insert(paymentOrder);

        log.info("Stripe支付订单创建(Mock): paymentId={}, outTradeNo={}, amount={}",
                paymentOrder.getId(), outTradeNo, order.getTotalAmount());

        // 4. 返回结果
        // 真实接入时：调用 Stripe PaymentIntent.create() 返回 clientSecret
        PaymentResultVo vo = new PaymentResultVo();
        vo.setPaymentId(paymentOrder.getId());
        vo.setOutTradeNo(outTradeNo);
        vo.setAmount(order.getTotalAmount());
        vo.setChannel(PaymentChannel.STRIPE.getCode());
        vo.setChannelName(PaymentChannel.STRIPE.getLabel());
        vo.setStatus(PaymentStatus.PENDING.getCode());
        vo.setStatusLabel("待支付");

        // Mock clientSecret（真实接入时由 Stripe API 返回）
        vo.setClientSecret("pi_mock_" + outTradeNo + "_secret_" + UUID.randomUUID().toString().substring(0, 8));

        return Result.success(vo);
    }

    @Override
    public PaymentStatus handleCallback(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String transactionId = params.get("payment_intent_id");
        String eventType = params.get("type"); // payment_intent.succeeded / payment_intent.payment_failed

        log.info("Stripe Webhook回调(Mock): outTradeNo={}, transactionId={}, eventType={}",
                outTradeNo, transactionId, eventType);

        if (outTradeNo == null) {
            log.error("Stripe回调缺少 out_trade_no 参数");
            return PaymentStatus.FAILED;
        }

        // 真实接入时：验证 webhook 签名
        // Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        // 根据 out_trade_no 查找支付订单
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentOrder>()
                        .eq("out_trade_no", outTradeNo));

        if (paymentOrder == null) {
            log.error("Stripe回调找不到对应支付订单: outTradeNo={}", outTradeNo);
            return PaymentStatus.FAILED;
        }

        // 幂等性检查
        if (paymentOrder.getStatus() != PaymentStatus.PENDING.getCode()) {
            log.info("支付订单已处理，忽略重复回调: paymentId={}", paymentOrder.getId());
            return PaymentStatus.fromCode(paymentOrder.getStatus());
        }

        // 处理支付结果
        if ("payment_intent.succeeded".equals(eventType)) {
            paymentOrder.setStatus(PaymentStatus.SUCCESS.getCode());
            paymentOrder.setTransactionId(transactionId);
            paymentOrder.setCallbackData(params.toString());
            paymentOrder.setCallbackTime(LocalDateTime.now());
            paymentOrderMapper.updateById(paymentOrder);

            // 同步更新订单状态
            TradeOrder order = traderOrderMapper.selectById(paymentOrder.getOrderId());
            if (order != null && order.getStatus() == 0) {
                order.setStatus(1);
                order.setPaymentType(PaymentChannel.STRIPE.getLabel());
                order.setPaymentId(paymentOrder.getId());
                order.setPayTime(LocalDateTime.now());
                traderOrderMapper.updateById(order);

                // 发送支付成功通知
                eventPublisher.publishEvent(new NotificationEvent(this,
                        order.getUserId(),
                        NotificationType.ORDER_STATUS.getCode(),
                        "支付成功",
                        "订单 #" + order.getId() + " 已通过 Stripe 支付成功，金额 ¥" + order.getTotalAmount() + "，等待商家发货。",
                        NotificationTargetType.ORDER.getCode(),
                        order.getId()));
            }

            return PaymentStatus.SUCCESS;
        } else {
            paymentOrder.setStatus(PaymentStatus.FAILED.getCode());
            paymentOrder.setCallbackData(params.toString());
            paymentOrder.setCallbackTime(LocalDateTime.now());
            paymentOrderMapper.updateById(paymentOrder);
            return PaymentStatus.FAILED;
        }
    }

    @Override
    public PaymentStatus queryStatus(Long paymentOrderId) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null) {
            return null;
        }
        // 真实接入时：调用 Stripe PaymentIntent.retrieve() 获取最新状态
        return PaymentStatus.fromCode(paymentOrder.getStatus());
    }

    @Override
    public Result<String> refund(Long paymentOrderId, BigDecimal amount) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null) {
            return Result.error(404, "支付订单不存在！");
        }
        if (paymentOrder.getStatus() != PaymentStatus.SUCCESS.getCode()) {
            return Result.error(400, "只有已支付的订单才能退款");
        }

        // 真实接入时：调用 Stripe Refund.create()
        // RefundCreateParams params = RefundCreateParams.builder()
        //     .setPaymentIntent(paymentOrder.getTransactionId())
        //     .setAmount(amount.multiply(new BigDecimal("100")).longValue()) // Stripe 以分为单位
        //     .build();
        // Refund refund = Refund.create(params);

        paymentOrder.setStatus(PaymentStatus.REFUNDED.getCode());
        paymentOrderMapper.updateById(paymentOrder);

        log.info("Stripe退款(Mock)成功: paymentId={}, amount={}", paymentOrderId, amount);

        return Result.success("Stripe退款已提交，预计5-10个工作日原路返回");
    }
}
