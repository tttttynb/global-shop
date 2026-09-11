package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.vo.NotificationVo;

import java.util.List;
import java.util.Map;

/**
 * 消息通知服务
 */
public interface NotificationService {

    /**
     * 获取用户通知列表（按时间倒序，最多100条）
     */
    Result<List<NotificationVo>> getNotifications(Long userId, Integer page, Integer size);

    /**
     * 获取未读通知数量
     */
    Result<Map<String, Object>> getUnreadCount(Long userId);

    /**
     * 标记单条通知为已读
     */
    Result<String> markAsRead(Long userId, Long notificationId);

    /**
     * 一键全部标记为已读
     */
    Result<String> markAllAsRead(Long userId);

    /**
     * 删除通知
     */
    Result<String> deleteNotification(Long userId, Long notificationId);
}
