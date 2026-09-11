package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.service.NotificationService;
import com.bohao.globalshop.vo.NotificationVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取通知列表
     */
    @GetMapping("/list")
    public Result<List<NotificationVo>> getNotifications(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return notificationService.getNotifications(userId, page, size);
    }

    /**
     * 获取未读数量
     */
    @GetMapping("/unread")
    public Result<Map<String, Object>> getUnreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return notificationService.getUnreadCount(userId);
    }

    /**
     * 标记单条已读
     */
    @PostMapping("/{id}/read")
    public Result<String> markAsRead(HttpServletRequest request, @PathVariable("id") Long notificationId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return notificationService.markAsRead(userId, notificationId);
    }

    /**
     * 全部已读
     */
    @PostMapping("/read-all")
    public Result<String> markAllAsRead(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return notificationService.markAllAsRead(userId);
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteNotification(HttpServletRequest request, @PathVariable("id") Long notificationId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return notificationService.deleteNotification(userId, notificationId);
    }
}
