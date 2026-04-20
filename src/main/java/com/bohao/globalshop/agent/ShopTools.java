package com.bohao.globalshop.agent;

import cn.hutool.json.JSONUtil;
import com.bohao.globalshop.controller.AiSearchController;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.ProductService;
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
    @Tool("当用户描述想买什么东西、寻找礼物、或者需要商品推荐时，调用此工具在商品库中检索")
    public String searchProducts(String keyword) {
        System.out.println("🤖 AI 正在后台偷偷执行高维度语义检索...");
        return JSONUtil.toJsonStr(aiSearchController.semanticSearch(keyword));
    }


}
