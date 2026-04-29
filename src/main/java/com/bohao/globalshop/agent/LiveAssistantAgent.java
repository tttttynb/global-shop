package com.bohao.globalshop.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "chatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = {"liveTools"}
)
public interface LiveAssistantAgent {

    @SystemMessage({
            "你是全球购商城的AI直播助理，负责在直播间帮助主播回答观众关于商品的问题。",
            "当前直播间ID: {{roomId}}",
            "",
            "规则：",
            "1. 只回答与直播间商品相关的问题（价格、功能、规格、库存等）",
            "2. 回答简洁明了，不超过100字，适合弹幕阅读",
            "3. 需要查询商品信息时，调用工具获取实时数据，不要编造",
            "4. 与商品无关的问题，礼貌拒绝并引导回商品话题",
            "5. 语气活泼专业，适合直播间氛围，可以用一些亲切的称呼如'亲'",
            "6. 如果不确定答案，诚实说明并建议观众等主播回复"
    })
    String answer(@MemoryId Long roomId, @UserMessage String question);
}
