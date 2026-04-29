package com.bohao.globalshop.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionManager {
    private final StringRedisTemplate stringRedisTemplate;

    // 🏠 每个直播间维护一组 WebSocket 连接
    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    private static final String VIEWER_COUNT_KEY_PREFIX = "live:viewer:count:";

    /**
     * 添加观众连接到指定直播间
     */
    public void addSession(Long roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
        // 更新 Redis 中的在线人数
        stringRedisTemplate.opsForValue().increment(VIEWER_COUNT_KEY_PREFIX + roomId);
        log.info("🟢 观众加入直播间 [{}]，当前连接数: {}", roomId, getViewerCount(roomId));
    }

    /**
     * 移除观众连接
     */
    public void removeSession(Long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            // 减少 Redis 中的在线人数
            stringRedisTemplate.opsForValue().decrement(VIEWER_COUNT_KEY_PREFIX + roomId);
            log.info("🔴 观众离开直播间 [{}]，当前连接数: {}", roomId, getViewerCount(roomId));
            // 如果直播间没人了，清理掉
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
                stringRedisTemplate.delete(VIEWER_COUNT_KEY_PREFIX + roomId);
            }
        }
    }

    /**
     * 获取直播间所有连接
     */
    public Set<WebSocketSession> getSessions(Long roomId) {
        return roomSessions.getOrDefault(roomId, Collections.emptySet());
    }

    /**
     * 获取直播间在线人数
     */
    public int getViewerCount(Long roomId) {
        String count = stringRedisTemplate.opsForValue().get(VIEWER_COUNT_KEY_PREFIX + roomId);
        return count != null ? Integer.parseInt(count) : 0;
    }

    /**
     * 向直播间所有连接广播消息
     */
    public void broadcastToRoom(Long roomId, String message) {
        Set<WebSocketSession> sessions = getSessions(roomId);
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("❌ 向直播间 [{}] 广播消息失败", roomId, e);
                }
            }
        }
    }
}
