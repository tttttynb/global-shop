package com.bohao.globalshop.config;

import com.bohao.globalshop.websocket.AudioStreamHandler;
import com.bohao.globalshop.websocket.LiveWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final LiveWebSocketHandler liveWebSocketHandler;
    private final AudioStreamHandler audioStreamHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveWebSocketHandler, "/ws/live/{roomId}")
                .setAllowedOrigins("*");
        registry.addHandler(audioStreamHandler, "/ws/live/{roomId}/audio")
                .setAllowedOrigins("*");
    }
}
