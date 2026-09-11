package com.bohao.globalshop.service.impl;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.PaymentCreateDto;
import com.bohao.globalshop.entity.PaymentOrder;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.enums.PaymentChannel;
import com.bohao.globalshop.mapper.PaymentOrderMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.PaymentService;
import com.bohao.globalshop.service.payment.PaymentGateway;
import com.bohao.globalshop.vo.PaymentResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付服务编排层实现
 * <p>
 * Spring 自动注入所有 PaymentGateway 实现类，构建 channel → gateway 路由表。
 * 新增支付渠道无需修改此类。
 */
@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final Map<PaymentChannel, PaymentGateway> gatewayMap;
    private final PaymentOrderMapper paymentOrderMapper;
    private final TraderOrderMapper traderOrderMapper;

    /**
     * 构造函数注入所有支付网关实现
     * <p>
     * Spring 自动收集所有 PaymentGateway 的 Bean，按 getChannel() 构建路由表
     */
    public PaymentServiceImpl(List<PaymentGateway> gateways,
                              PaymentOrderMapper paymentOrderMapper,
                              TraderOrderMapper traderOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.traderOrderMapper = traderOrderMapper;
        this.gatewayMap = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::getChannel, g -> g));
        log.info("支付网关加载完成，已注册 {} 个渠道: {}",
                gatewayMap.size(),
                gatewayMap.keySet().stream().map(PaymentChannel::getLabel).collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public Result<PaymentResultVo> createPayment(Long userId, PaymentCreateDto dto) {
        // 1. 参数校验
        if (dto.getOrderId() == null) {
            return Result.error(400, "订单ID不能为空");
        }
        if (dto.getChannel() == null) {
            return Result.error(400, "请选择支付方式");
        }

        // 2. 解析支付渠道
        PaymentChannel channel;
        try {
            channel = PaymentChannel.fromCode(dto.getChannel());
        } catch (IllegalArgumentException e) {
            return Result.error(400, "不支持的支付方式: " + dto.getChannel());
        }

        // 3. 查找对应的支付网关
        PaymentGateway gateway = gatewayMap.get(channel);
        if (gateway == null) {
            return Result.error(400, "该支付方式暂未开通: " + channel.getLabel());
        }

        // 4. 查询订单
        TradeOrder order = traderOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            return Result.error(404, "订单不存在！");
        }

        // 5. 委托给具体的支付网关处理
        log.info("创建支付: userId={}, orderId={}, channel={}", userId, dto.getOrderId(), channel.getLabel());
        return gateway.createPayment(userId, order);
    }

    @Override
    public Result<String> handleCallback(String channelName, Map<String, String> params) {
        PaymentChannel channel;
        try {
            channel = PaymentChannel.fromName(channelName);
        } catch (IllegalArgumentException e) {
            log.error("收到未知渠道的回调: channel={}, params={}", channelName, params);
            return Result.error(400, "未知的支付渠道: " + channelName);
        }

        PaymentGateway gateway = gatewayMap.get(channel);
        if (gateway == null) {
            return Result.error(400, "该支付渠道未注册: " + channel.getLabel());
        }

        log.info("处理支付回调: channel={}, params={}", channel.getLabel(), params);
        gateway.handleCallback(params);
        return Result.success("回调处理完成");
    }

    @Override
    public Result<PaymentOrder> queryPaymentStatus(Long userId, Long paymentOrderId) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null) {
            return Result.error(404, "支付订单不存在！");
        }
        if (!paymentOrder.getUserId().equals(userId)) {
            return Result.error(403, "无权查看此支付订单！");
        }

        // 如果是待支付状态，尝试从网关同步最新状态
        if (paymentOrder.getStatus() == 0) {
            PaymentChannel channel = PaymentChannel.fromCode(paymentOrder.getChannel());
            PaymentGateway gateway = gatewayMap.get(channel);
            if (gateway != null) {
                gateway.queryStatus(paymentOrderId);
                // 重新查询（网关可能更新了状态）
                paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
            }
        }

        return Result.success(paymentOrder);
    }

    @Override
    public Result<List<Map<String, Object>>> getSupportedChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();
        for (PaymentChannel channel : PaymentChannel.values()) {
            if (gatewayMap.containsKey(channel)) {
                Map<String, Object> info = new HashMap<>();
                info.put("code", channel.getCode());
                info.put("name", channel.name().toLowerCase());
                info.put("label", channel.getLabel());
                channels.add(info);
            }
        }
        return Result.success(channels);
    }
}
