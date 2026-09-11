package com.bohao.globalshop.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 个性化 AI 客服 Agent（Phase 3）
 * <p>
 * 与 {@link CustomerServiceAgent} 的区别：
 * <ul>
 *   <li>通过 {@code @V("tierPrompt")} 注入动态构建的用户层级 System Message</li>
 *   <li>使用 {@link PersonalizedShopTools}（ThreadLocal 自动获取 userId）</li>
 *   <li>Tool 调用时无需 LLM 传 userId，防止身份伪造</li>
 * </ul>
 * </p>
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "chatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = {"personalizedShopTools"}
)
public interface PersonalizedCustomerServiceAgent {

    /**
     * 个性化客服对话
     *
     * @param memoryId   记忆ID（用户级对话窗口）
     * @param userId     当前用户真实ID（由 JWT 解析，注入到 System Message 中）
     * @param tierPrompt 根据用户层级动态构建的角色提示词
     * @param userMessage 用户原始消息
     * @return AI 个性化回复
     */
    @SystemMessage({
        "{{tierPrompt}}",
        "",
        "当前与你聊天的买家专属ID是：{{uid}}。当需要查订单时，必须使用这个ID！",
        "",
        "核心工作准则：",
        "1. 称呼用户时使用其在对话中提及的昵称，或礼貌的尊称。",
        "2. 当用户问订单，你必须调用查订单工具，并贴心地告诉用户订单的状态（0=待支付, 1=已支付, 2=已取消, 3=已发货, 4=已收货, 5=已评价）。",
        "3. 当用户想买东西或需要商品推荐，必须调用商品检索工具，并用热情、有吸引力的方式向用户介绍商品（必须包含价格）。",
        "4. 推荐商品时，根据用户的消费层级调整推荐话术和侧重点。",
        "5. 如果用户聊与购物和订单无关的话题（如写代码、聊政治），请礼貌地引导回商城话题。"
    })
    String chat(@MemoryId Long memoryId,
                @V("uid") Long userId,
                @V("tierPrompt") String tierPrompt,
                @UserMessage String userMessage);
}
