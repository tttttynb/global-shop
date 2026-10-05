package com.bohao.globalshop.agent;

import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.controller.AiSearchController;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.ProductService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopTools {
    private final OrderService orderService;
    private final AiSearchController aiSearchController;
    private final ProductService productService;
    // 🚀 魔法注解：告诉 AI，如果买家想查订单，就自动调这个方法！
    // （括号里的中文描述非常极其重要，大模型就是靠这段中文来决定要不要执行的！）

    @Tool("当用户询问他自己的订单记录、发货状态时，调用此工具查询")
    public String getMyOrders(Long userId) {
        System.out.println(
                "🤖 AI 正在后台偷偷调用 Java 查订单..."
        );
        // 查出数据后转成 JSON 字符串，直接塞给大模型看！
        return
                JSONUtil.toJsonStr(orderService.getMyOrders(userId).getData());
    }

    // 🚀 魔法注解：告诉 AI，如果买家想买东西，就自动去向量库捞！
    @Tool("推荐或查找任何商品前必须先调用此工具获取真实商品数据，严禁凭记忆列举商品或声称没有货；用户提到预算时同样要调用本工具搜索。")
    public String searchProducts(@P("商品品类或需求关键词，例如「降噪耳机」「香水」，不要带价格与语气词") String keyword) {
        System.out.println("🤖 AI 正在后台偷偷执行高维度语义检索...");
        return JSONUtil.toJsonStr(aiSearchController.semanticSearch(keyword));
    }


}
