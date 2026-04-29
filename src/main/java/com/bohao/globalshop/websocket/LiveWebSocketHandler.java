package com.bohao.globalshop.websocket;

import cn.hutool.crypto.digest.DigestUtil;
import com.bohao.globalshop.agent.LiveAssistantAgent;
import com.bohao.globalshop.common.JwtUtils;
import com.bohao.globalshop.entity.LiveMessage;
import com.bohao.globalshop.entity.LiveRoom;
import com.bohao.globalshop.mapper.LiveMessageMapper;
import com.bohao.globalshop.mapper.LiveRoomMapper;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.vo.LiveMessageVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveWebSocketHandler extends TextWebSocketHandler {
    private final LiveSessionManager sessionManager;
    private final LiveMessageMapper liveMessageMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final LiveAssistantAgent liveAssistantAgent;
    private final LiveRoomMapper liveRoomMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 从URI中解析roomId: /ws/live/{roomId}
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String path = uri.getPath();
        String[] parts = path.split("/");
        Long roomId = Long.parseLong(parts[parts.length - 1]);

        // 2. 从URI查询参数中获取token，用JwtUtils验证获取userId
        String query = uri.getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String token = queryParams.get("token");

        if (token == null || token.isEmpty()) {
            log.warn("❌ WebSocket连接被拒绝：缺少token参数");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long userId = JwtUtils.verifyToken(token);
        if (userId == null) {
            log.warn("❌ WebSocket连接被拒绝：token验证失败");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 获取用户昵称
        var user = userMapper.selectById(userId);
        String nickname = user != null ? user.getNickname() : "用户" + userId;

        // 3. 将userId和roomId存入session attributes
        session.getAttributes().put("userId", userId);
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("nickname", nickname);

        // 4. 调用sessionManager添加连接
        sessionManager.addSession(roomId, session);

        // 5. 广播系统消息: "用户xxx进入直播间" (type=3)
        LiveMessageVo systemMsg = new LiveMessageVo();
        systemMsg.setUserId(userId);
        systemMsg.setNickname(nickname);
        systemMsg.setContent(nickname + "进入直播间");
        systemMsg.setType(3);
        systemMsg.setCreateTime(LocalDateTime.now());

        Map<String, Object> systemBroadcast = new HashMap<>();
        systemBroadcast.put("type", "message");
        systemBroadcast.put("data", systemMsg);
        sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(systemBroadcast));

        // 6. 广播在线人数更新
        broadcastViewerCount(roomId);

        log.info("✅ 用户 [{}] 连接到直播间 [{}]", nickname, roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // 1. 解析消息JSON: {content: "xxx", type: 0}
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());

            JsonNode contentNode = jsonNode.get("content");
            if (contentNode == null || contentNode.asText().isBlank()) {
                log.warn("收到非法弹幕消息：缺少content字段，session={}", session.getId());
                return;
            }
            String content = contentNode.asText();

            int type = 0;
            JsonNode typeNode = jsonNode.get("type");
            if (typeNode != null && typeNode.isInt()) {
                type = typeNode.asInt();
            }

            // 2. 从session attributes获取userId和roomId
            Long userId = (Long) session.getAttributes().get("userId");
            Long roomId = (Long) session.getAttributes().get("roomId");
            String nickname = (String) session.getAttributes().get("nickname");

            // 3. 构建LiveMessage实体并保存到数据库
            LiveMessage liveMessage = new LiveMessage();
            liveMessage.setLiveRoomId(roomId);
            liveMessage.setUserId(userId);
            liveMessage.setNickname(nickname);
            liveMessage.setContent(content);
            liveMessage.setType(type);
            liveMessage.setCreateTime(LocalDateTime.now());
            liveMessageMapper.insert(liveMessage);

            // 4. 构建LiveMessageVo
            LiveMessageVo vo = new LiveMessageVo();
            vo.setId(liveMessage.getId());
            vo.setUserId(userId);
            vo.setNickname(nickname);
            vo.setContent(content);
            vo.setType(type);
            vo.setCreateTime(liveMessage.getCreateTime());

            // 5. 广播消息到直播间所有连接
            Map<String, Object> broadcast = new HashMap<>();
            broadcast.put("type", "message");
            broadcast.put("data", vo);
            sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(broadcast));

            // 6. 尝试触发AI助理回答
            tryAiResponse(roomId, content, type);
        } catch (Exception e) {
            log.error("处理弹幕消息失败，session={}", session.getId(), e);
            // 不关闭连接，防止恶意消息导致DoS
        }
    }

    /**
     * 尝试触发AI助理自动回答商品提问
     */
    private void tryAiResponse(Long roomId, String content, int type) {
        try {
            // 1. 检查AI助理开关
            LiveRoom room = liveRoomMapper.selectById(roomId);
            if (room == null || !Boolean.TRUE.equals(room.getAiAssistantEnabled())) {
                return;
            }

            // 2. 检查是否是商品提问（type=1 为提问，或内容包含商品关键词）
            if (type != 1 && !isProductQuestion(content)) {
                return;
            }

            // 3. 频率控制
            String contentMd5 = DigestUtil.md5Hex(content);
            String answerCacheKey = "live:ai:answer:" + roomId + ":" + contentMd5;
            String cooldownKey = "live:ai:cooldown:" + roomId;

            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
                return; // 冷却中
            }

            String cachedAnswer = stringRedisTemplate.opsForValue().get(answerCacheKey);
            if (cachedAnswer != null) {
                broadcastAiResponse(roomId, cachedAnswer);
                return;
            }

            // 4. 异步调用AI
            CompletableFuture.runAsync(() -> {
                try {
                    String answer = liveAssistantAgent.answer(roomId, content);

                    // 设置冷却
                    stringRedisTemplate.opsForValue().set(cooldownKey, "1", 3, TimeUnit.SECONDS);
                    // 缓存回答
                    stringRedisTemplate.opsForValue().set(answerCacheKey, answer, 5, TimeUnit.MINUTES);

                    // 广播AI回复
                    broadcastAiResponse(roomId, answer);
                } catch (Exception e) {
                    log.error("❌ AI助理回答失败，直播间 [{}]", roomId, e);
                }
            });
        } catch (Exception e) {
            log.error("❌ AI助理触发检查失败，直播间 [{}]", roomId, e);
        }
    }

    /**
     * 判断弹幕是否为商品相关提问
     */
    private boolean isProductQuestion(String content) {
        String[] keywords = {"多少钱", "价格", "什么颜色", "颜色", "质量", "库存",
                "有没有", "怎么样", "好不好", "能不能", "功能", "材质", "尺寸", "尺码",
                "发货", "包邮", "规格", "参数", "保修", "售后", "优惠", "折扣", "活动"};
        for (String keyword : keywords) {
            if (content.contains(keyword)) return true;
        }
        return content.endsWith("?") || content.endsWith("？");
    }

    /**
     * 广播AI助理回复到直播间
     */
    private void broadcastAiResponse(Long roomId, String answer) {
        try {
            LiveMessageVo vo = new LiveMessageVo();
            vo.setNickname("AI助理");
            vo.setContent(answer);
            vo.setType(2); // AI回复类型
            vo.setCreateTime(LocalDateTime.now());

            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "message");
            wsMessage.put("data", vo);

            sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(wsMessage));
        } catch (Exception e) {
            log.error("❌ 广播AI回复失败，直播间 [{}]", roomId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 1. 从session attributes获取roomId
        Long roomId = (Long) session.getAttributes().get("roomId");
        String nickname = (String) session.getAttributes().get("nickname");

        if (roomId != null) {
            // 2. 调用sessionManager移除连接
            sessionManager.removeSession(roomId, session);
            // 3. 广播在线人数更新
            broadcastViewerCount(roomId);
            log.info("👋 用户 [{}] 离开直播间 [{}]", nickname, roomId);
        }
    }

    /**
     * 广播在线人数更新
     */
    private void broadcastViewerCount(Long roomId) throws Exception {
        int viewerCount = sessionManager.getViewerCount(roomId);
        Map<String, Object> countMsg = new HashMap<>();
        countMsg.put("type", "viewerCount");
        countMsg.put("data", viewerCount);
        sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(countMsg));
    }

    /**
     * 解析URL查询参数
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }
}
