package com.bohao.globalshop.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class RabbitMqConfig {
    // ================== 1. 定义名称常量（防止写错名字） ==================
    // 正常的订单延迟交换机和队列
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.routing.key";
    // 💀 死信交换机和死信队列（消息老死后掉进这里）
    public static final String ORDER_DEAD_EXCHANGE = "order.dead.exchange";
    public static final String ORDER_DEAD_QUEUE = "order.dead.queue";
    public static final String ORDER_DEAD_ROUTING_KEY = "order.dead.routing.key";

    // ================== 2. 创建 💀 死信组件 (坟墓) ==================
    @Bean
    public DirectExchange orderDeadExchange() {
        return new DirectExchange(ORDER_DEAD_EXCHANGE);
    }

    @Bean
    public Queue orderDeadQueue() {
        return new Queue(ORDER_DEAD_QUEUE);
    }

    @Bean
    public Binding orderDeadBinding() {
        return BindingBuilder.bind(orderDeadQueue()).to(orderDeadExchange()).with(ORDER_DEAD_ROUTING_KEY);
    }

    // ================== 3. 创建 ⏳ 正常延迟组件 (等待区) ==================
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    @Bean
    public Queue orderDelayQueue() {
        // 极其关键：给这个正常队列配置“死信流转规则”！
        HashMap<String, Object> args = new HashMap<>();
        // 规则1：消息在这个队列死了后，扔给哪个死信交换机？
        args.put("x-dead-letter-exchange", ORDER_DEAD_EXCHANGE);
        // 规则2：扔过去的时候，用什么暗号（RoutingKey）？
        args.put("x-dead-letter-routing-key", ORDER_DEAD_ROUTING_KEY);
        // 规则3：为了方便测试，我们把 TTL（寿命）设为 10 秒（真实电商环境是 15 分钟也就是 900000 毫秒）
        args.put("x-message-ttl", 10000);
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDeadQueue()).to(orderDelayExchange()).with(ORDER_DELAY_ROUTING_KEY);
    }
}
