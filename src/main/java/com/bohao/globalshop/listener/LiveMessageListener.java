package com.bohao.globalshop.listener;

import com.bohao.globalshop.config.RabbitMqConfig;
import com.bohao.globalshop.entity.LiveMessage;
import com.bohao.globalshop.mapper.LiveMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveMessageListener {
    private final LiveMessageMapper liveMessageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 🚀 监听弹幕消息队列，异步持久化弹幕到数据库（削峰处理）
     */
    @RabbitListener(queues = RabbitMqConfig.LIVE_MESSAGE_QUEUE)
    public void processLiveMessage(String messageJson) {
        log.info("💬 收到弹幕消息，准备持久化...");
        try {
            LiveMessage liveMessage = objectMapper.readValue(messageJson, LiveMessage.class);
            liveMessageMapper.insert(liveMessage);
            log.info("✅ 弹幕消息持久化成功，直播间: [{}]，用户: [{}]", liveMessage.getLiveRoomId(), liveMessage.getNickname());
        } catch (Exception e) {
            log.error("❌ 弹幕消息持久化失败：", e);
        }
    }
}
