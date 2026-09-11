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
 * 支付宝支付网关（占位实现）
 * <p>
 * 当前为 mock 模式，返回模拟的支付页面URL。
 * 接入真实支付宝时，将以下配置加入 application.yml 并置 enabled=true：
 * <pre>
 * app.payment.alipay:
 *   enabled: true
 *   app-id: 你的应用ID
 *   private-key: 你的应用私钥
 *   alipay-public-key: 支付宝公钥
 *   notify-url: https://你的域名/api/payment/callback/alipay
 *   sandbox: true  # 沙箱模式
 * </pre>
 * <p>
 * 真实接入步骤：
 * 1. 添加 alipay-sdk-java 依赖
 * 2. 用 AlipayClient 创建当面付/电脑网站支付/手机网站支付订单
 * 3. 实现 handleCallback() 中的 RSA 验签
 * 4. 实现 queryStatus() 调用支付宝查询接口
 * 5. 实现 refund() 调用支付宝退款接口
 */
@Slf4j
@Component("alipayPaymentGateway")
@RequiredArgsConstructor
public class AlipayPaymentGateway implements PaymentGateway {

    private final PaymentOrderMapper paymentOrderMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        log.info("================================================");
        log.info("  💳 支付宝支付模块已加载（占位/Mock 模式）");
        log.info("  如需启用真实支付，请在 application.yml 中配置:");
        log.info("  app.payment.alipay.enabled=true");
        log.info("  app.payment.alipay.app-id=你的应用ID");
        log.info("  app.payment.alipay.private-key=你的应用私钥");
        log.info("================================================");
    }

    @Override
    public PaymentChannel getChannel() {
        return PaymentChannel.ALIPAY;
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
        String outTradeNo = "GSAL" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 3. 创建待支付记录
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setUserId(userId);
        paymentOrder.setChannel(PaymentChannel.ALIPAY.getCode());
        paymentOrder.setChannelName(PaymentChannel.ALIPAY.getLabel());
        paymentOrder.setAmount(order.getTotalAmount());
        paymentOrder.setStatus(PaymentStatus.PENDING.getCode());
        paymentOrder.setOutTradeNo(outTradeNo);
        paymentOrderMapper.insert(paymentOrder);

        log.info("支付宝支付订单创建(Mock): paymentId={}, outTradeNo={}, amount={}",
                paymentOrder.getId(), outTradeNo, order.getTotalAmount());

        // 4. 返回模拟支付页面（真实接入时替换为支付宝SDK生成的支付链接）
        PaymentResultVo vo = new PaymentResultVo();
        vo.setPaymentId(paymentOrder.getId());
        vo.setOutTradeNo(outTradeNo);
        vo.setAmount(order.getTotalAmount());
        vo.setChannel(PaymentChannel.ALIPAY.getCode());
        vo.setChannelName(PaymentChannel.ALIPAY.getLabel());
        vo.setStatus(PaymentStatus.PENDING.getCode());
        vo.setStatusLabel("待支付");

        // Mock：生成模拟支付链接
        String mockPayUrl = "http://localhost:8080/api/payment/callback/alipay"
                + "?out_trade_no=" + outTradeNo
                + "&trade_no=MOCK" + System.currentTimeMillis()
                + "&total_amount=" + order.getTotalAmount()
                + "&status=success";
        vo.setPayUrl(mockPayUrl);
        vo.setQrCode(mockPayUrl); // 实际接入时这里返回二维码链接

        return Result.success(vo);
    }

    @Override
    public PaymentStatus handleCallback(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String transactionId = params.get("trade_no");
        String status = params.get("status");

        log.info("支付宝回调(Mock): outTradeNo={}, tradeNo={}, status={}",
                outTradeNo, transactionId, status);

        if (outTradeNo == null) {
            log.error("支付宝回调缺少 out_trade_no 参数");
            return PaymentStatus.FAILED;
        }

        // 根据 out_trade_no 查找支付订单
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentOrder>()
                        .eq("out_trade_no", outTradeNo));

        if (paymentOrder == null) {
            log.error("支付宝回调找不到对应支付订单: outTradeNo={}", outTradeNo);
            return PaymentStatus.FAILED;
        }

        // 幂等性检查：如果已经处理过此回调，直接返回
        if (paymentOrder.getStatus() != PaymentStatus.PENDING.getCode()) {
            log.info("支付订单已处理，忽略重复回调: paymentId={}, status={}",
                    paymentOrder.getId(), paymentOrder.getStatus());
            return PaymentStatus.fromCode(paymentOrder.getStatus());
        }

        // 处理支付结果
        if ("success".equals(status)) {
            paymentOrder.setStatus(PaymentStatus.SUCCESS.getCode());
            paymentOrder.setTransactionId(transactionId);
            paymentOrder.setCallbackData(params.toString());
            paymentOrder.setCallbackTime(LocalDateTime.now());
            paymentOrderMapper.updateById(paymentOrder);

            // 同步更新订单状态
            TradeOrder order = traderOrderMapper.selectById(paymentOrder.getOrderId());
            if (order != null && order.getStatus() == 0) {
                order.setStatus(1);
                order.setPaymentType(PaymentChannel.ALIPAY.getLabel());
                order.setPaymentId(paymentOrder.getId());
                order.setPayTime(LocalDateTime.now());
                traderOrderMapper.updateById(order);
                log.info("支付宝支付成功，订单已更新: orderId={}", order.getId());

                // 发送支付成功通知
                eventPublisher.publishEvent(new NotificationEvent(this,
                        order.getUserId(),
                        NotificationType.ORDER_STATUS.getCode(),
                        "支付成功",
                        "订单 #" + order.getId() + " 已通过支付宝支付成功，金额 ¥" + order.getTotalAmount() + "，等待商家发货。",
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
        // 真实接入时：调用支付宝查询接口，同步最新状态
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

        // 真实接入时：调用支付宝退款接口
        // alipayClient.execute(new AlipayTradeRefundRequest()...);

        paymentOrder.setStatus(PaymentStatus.REFUNDED.getCode());
        paymentOrderMapper.updateById(paymentOrder);

        log.info("支付宝退款(Mock)成功: paymentId={}, amount={}", paymentOrderId, amount);

        return Result.success("支付宝退款已提交，预计1-3个工作日到账");
    }
}
