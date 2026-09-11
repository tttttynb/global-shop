package com.bohao.globalshop.websocket;

import com.bohao.globalshop.common.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

/**
 * 通知推送 WebSocket 处理器
 * <p>
 * 连接路径: ws://host/ws/notification?token=xxx
 * <p>
 * 客户端连接后，服务端通过 NotificationSessionManager 向其实时推送通知。
 * 客户端不需要发送任何消息，只需要保持连接即可接收推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId == null) {
            log.warn("通知WebSocket连接被拒绝：无效的token");
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionManager.addSession(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 通知 WebSocket 是单向推送（服务端→客户端），客户端发来的消息忽略
        // 未来可扩展：客户端发送心跳 pong
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessionManager.removeSession(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("通知WebSocket传输错误: userId={}", extractUserId(session), exception);
        Long userId = extractUserId(session);
        if (userId != null) {
            sessionManager.removeSession(userId, session);
        }
    }

    /**
     * 从 WebSocket 连接参数中提取用户ID
     */
    private Long extractUserId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            String query = uri.getQuery();
            if (query == null) return null;

            // 解析 token 参数
            Map<String, String> params = parseQueryParams(query);
            String token = params.get("token");
            if (token == null) return null;

            return JwtUtils.verifyToken(token);
        } catch (Exception e) {
            log.error("解析通知WebSocket token失败", e);
            return null;
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return map;
    }
}
