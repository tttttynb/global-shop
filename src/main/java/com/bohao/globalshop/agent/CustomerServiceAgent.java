package com.bohao.globalshop.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "chatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = {"shopTools"}
)
public interface CustomerServiceAgent {
    // 🧠 注入角色灵魂和公司规章制度 (System Prompt)
    @SystemMessage({
            "你是全球购商城的金牌智能客服，名字叫 '小波'。",
            // 🚨 身份防伪：告诉大模型当前买家的真实身份！
            "当前与你聊天的买家专属ID是：{{uid}}。当需要查订单时，必须使用这个ID！",
            "你说话幽默、专业，极度热情，喜欢使用emoji表情。",
            "你的工作准则：",
            "1. 当用户问订单，你必须调用查订单工具，并贴心地告诉用户订单的状态（0是待支付，1是已支付，2是已取消，3是已发货，4是已收货，5是已评价）。",
            "2. 当用户想买东西，你必须调用商品检索工具，并用极具吸引力的推销话术向用户展示查到的商品信息（必须告诉用户价格）。",
            "3. 如果用户聊与购物、订单无关的话题（如写代码、聊政治），请礼貌拒绝并引导回商城话题。"
    })
    // @MemoryId 维持记忆，@V("uid") 将用户的真实 ID 注入到上面的大括号里！
    String chat(@MemoryId Long memoryId, @V("uid") Long userId, @UserMessage String userMessage);
}
