package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.ChatLog;

import java.util.List;
import java.util.Map;

/**
 * AI 客服对话日志服务（Phase 4）
 */
public interface ChatLogService {

    /**
     * 异步记录对话日志（不阻塞主流程）
     */
    void logAsync(ChatLog chatLog);

    /**
     * 按用户层级统计对话数据
     *
     * @return 各层级的对话次数、平均响应耗时
     */
    List<Map<String, Object>> statsByTier();

    /**
     * 按天统计对话量（最近N天）
     */
    List<Map<String, Object>> statsByDay(int days);
}
