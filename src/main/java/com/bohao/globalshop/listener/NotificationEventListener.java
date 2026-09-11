package com.bohao.globalshop.listener;

import com.bohao.globalshop.entity.Notification;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.NotificationMapper;
import com.bohao.globalshop.vo.NotificationVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通知事件监听器 — 异步处理通知落库 + WebSocket 实时推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationMapper notificationMapper;
    private final com.bohao.globalshop.websocket.NotificationSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * 监听通知事件：落库 + WebSocket 推送
     */
    @Async
    @EventListener
    public void handleNotificationEvent(NotificationEvent event) {
        // 1. 落库
        Notification notif = new Notification();
        notif.setUserId(event.getUserId());
        notif.setType(event.getType());
        notif.setTitle(event.getTitle());
        notif.setContent(event.getContent());
        notif.setTargetType(event.getTargetType());
        notif.setTargetId(event.getTargetId());
        notif.setIsRead(0);
        notif.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notif);

        log.info("📬 通知已入库: userId={}, type={}, title={}", event.getUserId(), event.getType(), event.getTitle());

        // 2. WebSocket 实时推送
        NotificationVo vo = toVo(notif);
        try {
            String json = objectMapper.writeValueAsString(vo);
            sessionManager.sendToUser(event.getUserId(), json);
        } catch (JsonProcessingException e) {
            log.error("通知序列化失败: notifId={}", notif.getId(), e);
        }
    }

    private NotificationVo toVo(Notification n) {
        NotificationVo vo = new NotificationVo();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setTargetType(n.getTargetType());
        vo.setTargetId(n.getTargetId());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
