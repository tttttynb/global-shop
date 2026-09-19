package com.bohao.globalshop.controller;

import com.bohao.globalshop.agent.CustomerServiceAgent;
import com.bohao.globalshop.agent.PersonalizedCustomerServiceAgent;
import com.bohao.globalshop.agent.ProductCardCollector;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.ChatLog;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.ChatLogService;
import com.bohao.globalshop.service.PersonalizationService;
import com.bohao.globalshop.service.PointsService;
import com.bohao.globalshop.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 购物顾问对话接口（Phase 3 个性化客服 → Phase 4 - F10 金牌导购）
 * <p>
 * 支持：
 * <ul>
 *   <li>个性化 Agent（Phase 3），System Prompt 已升级为金牌导购人设（Phase 4 - F10）</li>
 *   <li>A/B 切换 — {@code app.personalization.enabled=false} 时回退到通用 Agent</li>
 *   <li>会员等级信息注入提示词（Phase 4 - F8）</li>
 *   <li>🆕 响应结构 {reply, products}：Agent 工具命中的商品以卡片形式返回，前端可直接加购（F10）</li>
 *   <li>对话日志异步记录 + 响应耗时统计</li>
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
    private final PointsService pointsService;

    /** 个性化开关：true=个性化, false=通用（用于 A/B 对比测试） */
    @Value("${app.personalization.enabled:true}")
    private boolean personalizationEnabled;

    @GetMapping
    public Result<Map<String, Object>> talk(HttpServletRequest request, @RequestParam String message) {
        long startTime = System.currentTimeMillis();
        Long userId = (Long) request.getAttribute("currentUserId");

        // 1. 读取用户画像
        UserProfile profile = userProfileService.getProfile(userId);
        String tier = profile != null && profile.getUserTier() != null
                ? profile.getUserTier() : "NEW";

        String aiReply;
        boolean isPersonalized = false;
        List<ProductCardCollector.Card> cards = new ArrayList<>();

        try {
            // 2. 根据 A/B 开关选择 Agent
            if (personalizationEnabled) {
                // 个性化模式：层级提示词 + 🆕 会员等级/积分信息（Phase 4 - F8 专属客服入口）
                String tierPrompt = personalizationService.buildTierPrompt(profile);
                String memberPrompt = pointsService.getMemberLevelPrompt(userId);
                if (!memberPrompt.isBlank()) {
                    tierPrompt = tierPrompt + "\n\n" + memberPrompt;
                }
                log.debug("个性化对话: userId={}, tier={}", userId, tier);
                ProductCardCollector.clear();
                aiReply = personalizedAgent.chat(userId, userId, tierPrompt, message);
                cards = ProductCardCollector.getCards();
                isPersonalized = true;
            } else {
                // 通用模式（A/B 对照）
                log.debug("通用对话: userId={}, tier={}（个性化已关闭）", userId, tier);
                aiReply = genericAgent.chat(userId, userId, message);
            }
        } finally {
            // ThreadLocal 必须清理，防止线程池串数据
            ProductCardCollector.clear();
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

        log.info("对话完成: userId={}, tier={}, personalized={}, cards={}, {}ms",
                userId, tier, isPersonalized, cards.size(), responseTimeMs);

        // 4. 🆕 结构化响应（Phase 4 - F10）：回复文本 + 可加购商品卡片
        Map<String, Object> data = new HashMap<>();
        data.put("reply", aiReply);
        List<Map<String, Object>> products = new ArrayList<>();
        for (ProductCardCollector.Card card : cards) {
            Map<String, Object> p = new HashMap<>();
            p.put("id", card.getId());
            p.put("name", card.getName());
            p.put("price", card.getPrice());
            p.put("coverImage", card.getCoverImage());
            p.put("shopName", card.getShopName());
            products.add(p);
        }
        data.put("products", products);
        return Result.success(data);
    }
}
