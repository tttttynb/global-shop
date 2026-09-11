package com.bohao.globalshop.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 通知 WebSocket 会话管理器
 * <p>
 * 维护 userId → WebSocketSession 的映射，支持向指定用户实时推送通知。
 * 一个用户可以在多个设备/标签页打开，所以用 Set 存储。
 */
@Slf4j
@Component
public class NotificationSessionManager {

    /** userId → 该用户的所有 WebSocket 连接 */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /**
     * 添加用户连接
     */
    public void addSession(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("🔔 用户 [{}] 建立通知连接，当前连接数: {}", userId, getUserSessionCount(userId));
    }

    /**
     * 移除用户连接
     */
    public void removeSession(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
            log.info("🔕 用户 [{}] 断开通知连接，剩余连接数: {}", userId, getUserSessionCount(userId));
        }
    }

    /**
     * 获取用户当前连接数
     */
    public int getUserSessionCount(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 向指定用户的所有连接推送消息
     */
    public void sendToUser(Long userId, String message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return; // 用户不在线，消息已落库，下次打开页面时可以看到
        }
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("向用户 [{}] 推送通知失败", userId, e);
                }
            }
        }
    }

    /**
     * 获取在线用户总数
     */
    public int getOnlineUserCount() {
        return userSessions.size();
    }
}
