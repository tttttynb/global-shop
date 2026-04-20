package com.bohao.globalshop.listener;

import com.bohao.globalshop.config.RabbitMqConfig;
import com.bohao.globalshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelListener {
    private final OrderService orderService;

    // 🚀 核心黑科技：像保安一样，死死盯住“死信队列（坟墓）”！只要里面掉进来订单 ID，立刻抓出来处理！
    @RabbitListener(queues = RabbitMqConfig.ORDER_DEAD_QUEUE)
    public void processDeadOrder(Long orderId){
        log.info("💀 警报！收到死信消息！订单号 [{}] 已超过支付时间，准备执行自动取消...", orderId);
        try {
            orderService.cancelSingleOrder(orderId);
            log.info("✅ 订单 [{}] 处理完毕！(如果它没付钱，就已经被取消并退还库存了)", orderId);
        }catch (Exception e){
            log.error("❌ 处理死信订单 [{}] 时发生异常：", orderId, e);
            // 大厂实战：如果这里抛异常了，MQ 默认会不断重试，或者进入死信的死信。目前咱们先打个日志。
        }
    }

}
