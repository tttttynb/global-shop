package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.ChatLog;
import com.bohao.globalshop.mapper.ChatLogMapper;
import com.bohao.globalshop.service.ChatLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 客服对话日志服务实现
 * <p>
 * 使用异步写入，避免日志记录影响对话响应速度
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogServiceImpl implements ChatLogService {

    private final ChatLogMapper chatLogMapper;

    @Override
    @Async
    public void logAsync(ChatLog chatLog) {
        try {
            chatLogMapper.insert(chatLog);
            log.debug("对话日志已记录: userId={}, tier={}, toolCalled={}, responseTimeMs={}",
                    chatLog.getUserId(), chatLog.getUserTier(),
                    chatLog.getToolCalled(), chatLog.getResponseTimeMs());
        } catch (Exception e) {
            log.error("记录对话日志失败: userId={}", chatLog.getUserId(), e);
        }
    }

    @Override
    public List<Map<String, Object>> statsByTier() {
        // 简化实现：按 user_tier 分组统计
        List<ChatLog> allLogs = chatLogMapper.selectList(null);
        if (allLogs == null || allLogs.isEmpty()) {
            return Collections.emptyList();
        }

        // 内存聚合
        Map<String, TierStats> tierStatsMap = new LinkedHashMap<>();
        String[] tiers = {"PREMIUM", "MID", "BUDGET", "NEW"};
        for (String tier : tiers) {
            tierStatsMap.put(tier, new TierStats());
        }
        tierStatsMap.put("UNKNOWN", new TierStats());

        for (ChatLog log : allLogs) {
            String tier = log.getUserTier() != null ? log.getUserTier() : "UNKNOWN";
            TierStats stats = tierStatsMap.computeIfAbsent(tier, k -> new TierStats());
            stats.count++;
            if (log.getResponseTimeMs() != null) {
                stats.totalTimeMs += log.getResponseTimeMs();
            }
            if (log.getToolCalled() != null && log.getToolCalled()) {
                stats.toolCallCount++;
            }
            if (log.getFeedback() != null && log.getFeedback()) {
                stats.positiveCount++;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, TierStats> entry : tierStatsMap.entrySet()) {
            if (entry.getValue().count == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tier", entry.getKey());
            row.put("chatCount", entry.getValue().count);
            row.put("avgResponseMs", entry.getValue().count > 0
                    ? entry.getValue().totalTimeMs / entry.getValue().count : 0);
            row.put("toolCallRate", entry.getValue().count > 0
                    ? String.format("%.1f%%", entry.getValue().toolCallCount * 100.0 / entry.getValue().count) : "0%");
            row.put("positiveRate", entry.getValue().count > 0
                    ? String.format("%.1f%%", entry.getValue().positiveCount * 100.0 / entry.getValue().count) : "0%");
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> statsByDay(int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        for (int i = 0; i < days; i++) {
            LocalDateTime dayStart = since.plusDays(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            QueryWrapper<ChatLog> wrapper = new QueryWrapper<>();
            wrapper.between("create_time", dayStart, dayEnd);
            long count = chatLogMapper.selectCount(wrapper);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dayStart.toLocalDate().toString());
            row.put("count", count);
            result.add(row);
        }
        return result;
    }

    @Override
    public String toString() {
        return "ChatLogServiceImpl{}";
    }

    // ==================== 内部聚合类 ====================

    private static class TierStats {
        int count;
        long totalTimeMs;
        int toolCallCount;
        int positiveCount;
    }
}
