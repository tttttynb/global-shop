package com.bohao.globalshop.controller;

import com.bohao.globalshop.agent.CustomerServiceAgent;
import com.bohao.globalshop.agent.PersonalizedCustomerServiceAgent;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.ChatLog;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.ChatLogService;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服对话接口（Phase 3+4）
 * <p>
 * 支持：
 * <ul>
 *   <li>个性化 Agent（Phase 3）</li>
 *   <li>A/B 切换 — {@code app.personalization.enabled=false} 时回退到通用 Agent</li>
 *   <li>对话日志异步记录（Phase 4.1）</li>
 *   <li>响应耗时统计（Phase 4.4）</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final PersonalizedCustomerServiceAgent personalizedAgent;
    private final CustomerServiceAgent genericAgent;
    private final UserProfileService userProfileService;
    private final PersonalizationService personalizationService;
    private final ChatLogService chatLogService;

    /** 个性化开关：true=个性化, false=通用（用于 A/B 对比测试） */
    @Value("${app.personalization.enabled:true}")
    private boolean personalizationEnabled;

    @GetMapping
    public Result<String> talk(HttpServletRequest request, @RequestParam String message) {
        long startTime = System.currentTimeMillis();
        Long userId = (Long) request.getAttribute("currentUserId");

        // 1. 读取用户画像
        UserProfile profile = userProfileService.getProfile(userId);
        String tier = profile != null && profile.getUserTier() != null
                ? profile.getUserTier() : "NEW";

        String aiReply;
        boolean isPersonalized = false;

        // 2. 根据 A/B 开关选择 Agent
        if (personalizationEnabled) {
            // 个性化模式
            String tierPrompt = personalizationService.buildTierPrompt(profile);
            log.debug("个性化对话: userId={}, tier={}", userId, tier);
            aiReply = personalizedAgent.chat(userId, userId, tierPrompt, message);
            isPersonalized = true;
        } else {
            // 通用模式（A/B 对照）
            log.debug("通用对话: userId={}, tier={}（个性化已关闭）", userId, tier);
            aiReply = genericAgent.chat(userId, userId, message);
        }

        int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

        // 3. 异步记录对话日志（不阻塞响应）
        try {
            ChatLog chatLog = new ChatLog();
            chatLog.setUserId(userId);
            chatLog.setUserTier(tier);
            chatLog.setUserMessage(message);
            chatLog.setAiResponse(aiReply);
            chatLog.setIsPersonalized(isPersonalized);
            chatLog.setResponseTimeMs(responseTimeMs);
            chatLog.setMessageLength(message.length());
            chatLog.setResponseLength(aiReply != null ? aiReply.length() : 0);
            // toolCalled + toolNames 可后续通过 AOP 或 Agent 回调增强
            chatLogService.logAsync(chatLog);
        } catch (Exception e) {
            log.warn("记录对话日志失败（不影响主流程）: {}", e.getMessage());
        }

        log.info("对话完成: userId={}, tier={}, personalized={}, {}ms",
                userId, tier, isPersonalized, responseTimeMs);

        return Result.success(aiReply);
    }
}
