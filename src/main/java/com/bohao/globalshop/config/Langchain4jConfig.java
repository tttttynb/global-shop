package com.bohao.globalshop.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Langchain4jConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    // 🚀 为每个来聊天的用户，分配一个专属的"记忆窗口"
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // withMaxMessages(20) 表示记住该用户最近的 20 条对话，防止聊得太多撑爆上下文
        return memoryId -> MessageWindowChatMemory.withMaxMessages(20);
    }

    // 🚀 核心修复：手动创建 ChatModel Bean，供 @AiService 使用
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    // 🚀 手动点火：专门用于"看图"的视觉大模型引擎！
    @Bean("visionModel")
    public ChatModel visionModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl) {

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("qwen-vl-max") // 🚨 极其关键：指定通义千问视觉大模型
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
