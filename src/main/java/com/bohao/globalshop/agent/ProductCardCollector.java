package com.bohao.globalshop.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话商品卡片收集器（Phase 4 - F10）
 * <p>
 * Agent 工具（搜索/成套推荐）执行时把命中商品写入 ThreadLocal，
 * ChatController 在对话结束后取出，随回复一起返回给前端渲染"可加购商品卡片"。
 * LangChain4j AiServices 同步执行，工具调用与请求同线程，ThreadLocal 安全。
 * ⚠️ 调用方必须在 finally 中 clear()，防止线程池串数据。
 */
public class ProductCardCollector {

    @Data
    public static class Card {
        private Long id;
        private String name;
        private BigDecimal price;
        private String coverImage;
        private String shopName;
    }

    private static final ThreadLocal<Map<Long, Card>> HOLDER = ThreadLocal.withInitial(LinkedHashMap::new);

    public static void add(Card card) {
        if (card != null && card.getId() != null) {
            HOLDER.get().putIfAbsent(card.getId(), card);
        }
    }

    public static List<Card> getCards() {
        return new ArrayList<>(HOLDER.get().values());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
