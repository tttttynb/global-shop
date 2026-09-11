package com.bohao.globalshop.service.payment.impl;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.PaymentOrder;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.enums.NotificationTargetType;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.enums.PaymentChannel;
import com.bohao.globalshop.enums.PaymentStatus;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.PaymentOrderMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.service.payment.PaymentGateway;
import com.bohao.globalshop.vo.PaymentResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 余额支付网关
 * <p>
 * 从用户平台余额中直接扣款，同步完成支付，无需第三方回调。
 * 这是平台最基础的支付方式，也是历史数据兼容的默认渠道。
 */
@Slf4j
@Component("balancePaymentGateway")
@RequiredArgsConstructor
public class BalancePaymentGateway implements PaymentGateway {

    private final UserMapper userMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PaymentChannel getChannel() {
        return PaymentChannel.BALANCE;
    }

    @Override
    @Transactional
    public Result<PaymentResultVo> createPayment(Long userId, TradeOrder order) {
        // 1. 安全校验：订单归属
        if (!order.getUserId().equals(userId)) {
            return Result.error(403, "警告：非法请求！这并非您的订单！");
        }

        // 2. 安全校验：防止重复支付，区分不同状态给出明确提示
        if (order.getStatus() != 0) {
            if (order.getStatus() == 1) {
                return Result.error(400, "这笔订单已经支付过啦，请勿重复付款！");
            } else if (order.getStatus() == 2) {
                return Result.error(400, "该订单已超时自动取消，请重新下单！");
            } else {
                return Result.error(400, "该订单当前状态（" + order.getStatus() + "）无法支付，如有疑问请联系客服。");
            }
        }

        // 3. 查出用户的钱包余额
        User user = userMapper.selectById(userId);

        // 4. 判断余额是否充足
        if (user.getBalance() == null || user.getBalance().compareTo(order.getTotalAmount()) < 0) {
            return Result.error(400, "老板，您的余额不足啦，请先充值！余额：" +
                    (user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO) + " 元");
        }

        // 5. 创建支付订单记录
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setUserId(userId);
        paymentOrder.setChannel(PaymentChannel.BALANCE.getCode());
        paymentOrder.setChannelName(PaymentChannel.BALANCE.getLabel());
        paymentOrder.setAmount(order.getTotalAmount());
        paymentOrder.setStatus(PaymentStatus.SUCCESS.getCode());
        paymentOrder.setOutTradeNo(generateOutTradeNo());
        paymentOrder.setTransactionId("BALANCE_" + System.currentTimeMillis());
        paymentOrderMapper.insert(paymentOrder);

        // 6. 扣减余额
        user.setBalance(user.getBalance().subtract(order.getTotalAmount()));
        userMapper.updateById(user);

        // 7. 更新订单状态
        order.setStatus(1);
        order.setPaymentType(PaymentChannel.BALANCE.getLabel());
        order.setPaymentId(paymentOrder.getId());
        order.setPayTime(LocalDateTime.now());
        traderOrderMapper.updateById(order);

        log.info("余额支付成功: userId={}, orderId={}, amount={}, paymentId={}",
                userId, order.getId(), order.getTotalAmount(), paymentOrder.getId());

        // 发送支付成功通知
        eventPublisher.publishEvent(new NotificationEvent(this,
                userId,
                NotificationType.ORDER_STATUS.getCode(),
                "支付成功",
                "订单 #" + order.getId() + " 已支付成功，金额 ¥" + order.getTotalAmount() + "，等待商家发货。",
                NotificationTargetType.ORDER.getCode(),
                order.getId()));

        // 8. 返回结果
        PaymentResultVo vo = new PaymentResultVo();
        vo.setPaymentId(paymentOrder.getId());
        vo.setOutTradeNo(paymentOrder.getOutTradeNo());
        vo.setAmount(order.getTotalAmount());
        vo.setChannel(PaymentChannel.BALANCE.getCode());
        vo.setChannelName(PaymentChannel.BALANCE.getLabel());
        vo.setStatus(PaymentStatus.SUCCESS.getCode());
        vo.setStatusLabel("支付成功");

        return Result.success(vo);
    }

    @Override
    public PaymentStatus handleCallback(Map<String, String> params) {
        // 余额支付是同步扣款，不需要回调处理
        log.warn("余额支付无需回调，收到回调请求已忽略: {}", params);
        return PaymentStatus.SUCCESS;
    }

    @Override
    public PaymentStatus queryStatus(Long paymentOrderId) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null) {
            return null;
        }
        return PaymentStatus.fromCode(paymentOrder.getStatus());
    }

    @Override
    @Transactional
    public Result<String> refund(Long paymentOrderId, BigDecimal amount) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null) {
            return Result.error(404, "支付订单不存在！");
        }

        // 退款到用户余额
        User user = userMapper.selectById(paymentOrder.getUserId());
        user.setBalance(user.getBalance().add(amount));
        userMapper.updateById(user);

        // 更新支付订单状态
        paymentOrder.setStatus(PaymentStatus.REFUNDED.getCode());
        paymentOrderMapper.updateById(paymentOrder);

        log.info("余额退款成功: paymentId={}, amount={}, userId={}",
                paymentOrderId, amount, paymentOrder.getUserId());

        return Result.success("退款成功！已退还 " + amount + " 元到您的余额");
    }

    private String generateOutTradeNo() {
        return "GSBL" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
