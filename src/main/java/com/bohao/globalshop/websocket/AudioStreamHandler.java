package com.bohao.globalshop.websocket;

import com.bohao.globalshop.common.JwtUtils;
import com.bohao.globalshop.entity.LiveRoom;
import com.bohao.globalshop.mapper.LiveRoomMapper;
import com.bohao.globalshop.service.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioStreamHandler extends BinaryWebSocketHandler {

    private final TranslationService translationService;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 从URI解析roomId: /ws/live/{roomId}/audio
        Long roomId = extractRoomId(session);
        // 2. 从URL参数获取token验证身份（必须是主播）
        String token = extractToken(session);
        Long userId = JwtUtils.verifyToken(token);

        // 3. 验证是否是该直播间的主播
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null || !room.getUserId().equals(userId)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 4. 从URL参数获取目标翻译语言，默认英语
        String targetLang = extractParam(session, "lang", "en");

        // 5. 存储roomId到session attributes
        session.getAttributes().put("roomId", roomId);

        // 6. 启动ASR会话，注册回调
        translationService.startAsrSession(roomId, targetLang, new TranslationService.AsrResultCallback() {
            @Override
            public void onResult(String text, boolean isFinal) {
                if (isFinal) {
                    // 最终结果：翻译后一起推送
                    String translated = translationService.translate(text, targetLang);
                    String subtitleJson = buildSubtitleMessage(text, translated, targetLang, true);
                    sessionManager.broadcastToRoom(roomId, subtitleJson);

                    // 新增：TTS语音合成 + 音频推送（异步执行，不阻塞字幕推送）
                    CompletableFuture.runAsync(() -> {
                        try {
                            byte[] audioData = translationService.synthesizeSpeech(translated, targetLang);
                            if (audioData != null && audioData.length > 0) {
                                broadcastTtsAudio(roomId, audioData, targetLang);
                            }
                        } catch (Exception e) {
                            log.error("TTS合成推送失败 - 直播间: {}", roomId, e);
                        }
                    });
                } else {
                    // 中间结果：直接推送原文（正在识别效果）
                    String subtitleJson = buildSubtitleMessage(text, null, targetLang, false);
                    sessionManager.broadcastToRoom(roomId, subtitleJson);
                }
            }

            @Override
            public void onError(String error) {
                log.error("ASR错误 - 直播间: {}, 错误: {}", roomId, error);
            }
        });

        log.info("音频流连接已建立 - 直播间: {}, 主播: {}, 目标语言: {}", roomId, userId, targetLang);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        Long roomId = (Long) session.getAttributes().get("roomId");
        if (roomId != null) {
            // 将音频二进制数据发送到ASR服务
            byte[] audioData = message.getPayload().array();
            translationService.sendAudioData(roomId, audioData);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long roomId = (Long) session.getAttributes().get("roomId");
        if (roomId != null) {
            translationService.stopAsrSession(roomId);
            log.info("音频流连接已关闭 - 直播间: {}", roomId);
        }
    }

    private Long extractRoomId(WebSocketSession session) {
        // 从URI路径 /ws/live/{roomId}/audio 中提取roomId
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        // path: /ws/live/123/audio -> parts[3] = "123"
        return Long.parseLong(parts[3]);
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private String extractParam(WebSocketSession session, String name, String defaultValue) {
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && name.equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return defaultValue;
    }

    private String buildSubtitleMessage(String sourceText, String translatedText, String targetLang, boolean isFinal) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"subtitle\",\"data\":{");
        json.append("\"sourceText\":\"").append(escapeJson(sourceText)).append("\",");
        if (translatedText != null) {
            json.append("\"translatedText\":\"").append(escapeJson(translatedText)).append("\",");
        }
        json.append("\"sourceLang\":\"zh\",");
        json.append("\"targetLang\":\"").append(targetLang).append("\",");
        json.append("\"isFinal\":").append(isFinal);
        json.append("}}");
        return json.toString();
    }

    /**
     * 向直播间广播TTS音频数据
     * 通过WebSocket发送Base64编码的音频JSON消息
     * 前端通过Web Audio API播放
     */
    private void broadcastTtsAudio(Long roomId, byte[] audioData, String lang) {
        // 先发送控制消息告知前端即将推送音频
        String controlMsg = String.format(
            "{\"type\":\"tts_start\",\"data\":{\"lang\":\"%s\",\"size\":%d}}",
            lang, audioData.length
        );
        sessionManager.broadcastToRoom(roomId, controlMsg);

        // Base64编码后作为JSON发送（兼容性好）
        String audioBase64 = Base64.getEncoder().encodeToString(audioData);
        String audioMsg = String.format(
            "{\"type\":\"tts_audio\",\"data\":{\"audio\":\"%s\",\"format\":\"pcm\",\"sampleRate\":16000,\"lang\":\"%s\"}}",
            audioBase64, lang
        );
        sessionManager.broadcastToRoom(roomId, audioMsg);
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}
