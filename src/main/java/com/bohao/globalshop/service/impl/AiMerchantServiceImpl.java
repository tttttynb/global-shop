package com.bohao.globalshop.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.dto.AiProductAnalysisDto;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiMerchantServiceImpl {
    @Autowired
    @Qualifier("visionModel")
    private ChatModel visionModel;

    public AiProductAnalysisDto analyzeImage(String imageUrl, String keyword) {
        try {
            // 1. 🧙‍♂️ 极其严苛的系统提示词（Prompt Engineering）：逼迫它输出标准 JSON
            String prompt = "你是一个拥有10年经验的跨境电商金牌运营。请分析这张商品图片，并结合关键词：【" + keyword + "】。\n" +
                    "请为我生成：\n" +
                    "1. name: 极具吸引力、适合SEO搜索的商品标题 (中文)\n" +
                    "2. description: 包含emoji表情的200字五星级商品描述 (中文)，用于吸引买家下单\n" +
                    "3. tags: 3到5个极其精准的商品分类标签\n" +
                    "🚨【极其重要】：你必须严格按照以下 JSON 格式返回，不要有任何 Markdown 标记，不要有任何其他多余的寒暄废话！\n" +
                    "{\"name\": \"标题\", \"description\": \"描述\", \"tags\": [\"标签1\", \"标签2\"]}";
            
            // 2. ⚡ 下载图片并转换为 Base64（设置超时时间）
            String base64Image;
            if (imageUrl != null && imageUrl.startsWith("data:image")) {
                // 处理 data URI 格式：data:image/xxx;base64,xxxxx
                int base64Index = imageUrl.indexOf("base64,");
                if (base64Index != -1) {
                    base64Image = imageUrl.substring(base64Index + 7);
                } else {
                    throw new IllegalArgumentException("无效的 data URI 格式");
                }
                log.info("使用前端提供的 base64 图片数据，长度: {}", base64Image.length());
            } else {
                // 处理普通 HTTP/HTTPS URL
                log.info("正在下载图片: {}", imageUrl);
                byte[] imageBytes = HttpUtil.downloadBytes(imageUrl);
                base64Image = Base64.encode(imageBytes);
                log.info("图片下载成功，Base64长度: {}", base64Image.length());
            }
            
            // 3. 组装多模态消息（文字 + Base64图像）
            UserMessage userMessage = UserMessage.from(
                    TextContent.from(prompt),
                    ImageContent.from(base64Image, "image/jpeg")
            );
            
            // 4. 呼叫大模型，获取纯文本 JSON 结果
            String jsonResponse = visionModel.chat(userMessage).aiMessage().text();
            
            // 5. 清洗数据：防止大模型自作聪明加上 ``json 的代码块标记
            jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
            
            log.info("AI Analysis Response: {}", jsonResponse);
            
            // 6. 💥 降维打击：直接把 JSON 字符串反序列化成我们的 Java 对象！
            return JSONUtil.toBean(jsonResponse, AiProductAnalysisDto.class);
            
        } catch (Exception e) {
            log.error("AI 图像分析失败, imageUrl: {}, keyword: {}", imageUrl, keyword, e);
            // 发生异常时，为了不阻断流程，我们可以返回一个带有默认值的对象，或者抛出自定义业务异常
            // 这里我们返回一个带有错误提示信息的 DTO
            AiProductAnalysisDto errorDto = new AiProductAnalysisDto();
            errorDto.setName("智能分析失败，请手动填写标题");
            errorDto.setDescription("很抱歉，AI 助手当前有些忙碌或图片无法访问，请您手动补充商品描述。（错误信息：" + e.getMessage() + "）");
            return errorDto;
        }
    }
}
