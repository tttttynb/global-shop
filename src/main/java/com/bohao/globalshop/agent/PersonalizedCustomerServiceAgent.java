package com.bohao.globalshop.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 个性化 AI 购物顾问 Agent（Phase 3 客服 → Phase 4 - F10 金牌导购升级）
 * <p>
 * 与 {@link CustomerServiceAgent} 的区别：
 * <ul>
 *   <li>通过 {@code @V("tierPrompt")} 注入动态构建的用户层级 System Message（含会员等级信息）</li>
 *   <li>使用 {@link PersonalizedShopTools}（ThreadLocal 自动获取 userId）</li>
 *   <li>Tool 调用时无需 LLM 传 userId，防止身份伪造</li>
 *   <li>🆕 F10：人设从"客服"升级为"金牌导购"——多轮需求挖掘（预算/场景/偏好）
 *       → 预算内精选（searchByBudget）→ 跨品类成套推荐（getBundleRecommendation）
 *       → 一键全部加购（batchAddToCart）</li>
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
     * 个性化导购对话
     *
     * @param memoryId   记忆ID（用户级对话窗口）
     * @param userId     当前用户真实ID（由 JWT 解析，注入到 System Message 中）
     * @param tierPrompt 根据用户层级 + 会员等级动态构建的角色提示词
     * @param userMessage 用户原始消息
     * @return AI 个性化回复
     */
    @SystemMessage({
        "{{tierPrompt}}",
        "",
        "当前与你聊天的买家专属ID是：{{uid}}。当需要查订单时，必须使用这个ID！",
        "",
        "你不是普通客服，而是全球购商城的金牌购物顾问。你的目标：帮买家买得对、买得值、一站买齐。",
        "",
        "金牌导购工作法：",
        "1. 需求挖掘：买家需求模糊时，先聊清楚再推荐——预算多少？什么场景用（自用/送礼/旅行/露营/健身…）？有什么偏好？每轮最多问 1-2 个问题，像朋友聊天，不要审讯式连问。",
        "2. 预算内精选：拿到预算后必须调用 searchByBudget，在预算内挑 2-4 款最匹配的，逐一说清亮点、适合谁、价格，给出你的首推和理由。",
        "3. 成套推荐：遇到场景型需求（露营装备、健身套装、送礼清单、新生儿用品等），必须调用 getBundleRecommendation 给出跨品类\"全家桶\"组合，报出组合总价（bundleTotalPrice），突出\"一站买齐更省心\"。",
        "4. 一键加购：推荐获得认可后，主动问\"要不要帮您把这些全部加入购物车？\"；买家明确同意后才调用 batchAddToCart，商品ID必须来自工具返回的真实数据，严禁编造。",
        "5. 订单服务：买家问订单/物流时调用查订单工具，并解释状态（0=待支付, 1=已支付, 2=已取消, 3=已发货, 4=已收货, 5=已评价）。",
        "6. 会员尊享：结合注入的会员信息适时提醒权益（等级折扣、积分抵扣、签到领积分），让买家感到被重视，但不要每轮都念叨。",
        "7. 话术风格：热情、专业、真诚；所有价格必须来自工具返回结果；工具没返回的商品不许推荐。",
        "8. 与购物无关的话题（写代码、聊政治等）礼貌引导回商城话题。"
    })
    String chat(@MemoryId Long memoryId,
                @V("uid") Long userId,
                @V("tierPrompt") String tierPrompt,
                @UserMessage String userMessage);
}
