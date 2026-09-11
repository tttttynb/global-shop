package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Notification;
import com.bohao.globalshop.entity.UserNotificationSetting;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.mapper.NotificationMapper;
import com.bohao.globalshop.mapper.UserNotificationSettingMapper;
import com.bohao.globalshop.service.NotificationService;
import com.bohao.globalshop.vo.NotificationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserNotificationSettingMapper settingMapper;

    @Override
    public Result<List<NotificationVo>> getNotifications(Long userId, Integer page, Integer size) {
        int p = page != null && page > 0 ? page : 1;
        int s = size != null && size > 0 ? Math.min(size, 50) : 20;

        QueryWrapper<Notification> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
                .orderByDesc("create_time");

        Page<Notification> mpPage = new Page<>(p, s);
        Page<Notification> result = notificationMapper.selectPage(mpPage, qw);

        List<NotificationVo> voList = result.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());

        // 构建分页信息
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("list", voList);
        extra.put("total", result.getTotal());
        extra.put("page", p);
        extra.put("size", s);

        return Result.success(voList);
    }

    @Override
    public Result<Map<String, Object>> getUnreadCount(Long userId) {
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("is_read", 0);
        long count = notificationMapper.selectCount(qw);

        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", count);
        return Result.success(result);
    }

    @Override
    public Result<String> markAsRead(Long userId, Long notificationId) {
        Notification notif = notificationMapper.selectById(notificationId);
        if (notif == null) {
            return Result.error(404, "通知不存在");
        }
        if (!notif.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        if (notif.getIsRead() == 0) {
            notif.setIsRead(1);
            notif.setReadTime(LocalDateTime.now());
            notificationMapper.updateById(notif);
        }
        return Result.success("已标为已读");
    }

    @Override
    public Result<String> markAllAsRead(Long userId) {
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("is_read", 0);

        Notification update = new Notification();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        notificationMapper.update(update, qw);

        return Result.success("全部已读");
    }

    @Override
    public Result<String> deleteNotification(Long userId, Long notificationId) {
        Notification notif = notificationMapper.selectById(notificationId);
        if (notif == null) {
            return Result.error(404, "通知不存在");
        }
        if (!notif.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        notificationMapper.deleteById(notificationId);
        return Result.success("已删除");
    }

    /**
     * 确保用户有通知设置记录（没有则创建默认全部开启）
     */
    public UserNotificationSetting getOrCreateSetting(Long userId) {
        UserNotificationSetting setting = settingMapper.selectById(userId);
        if (setting == null) {
            setting = new UserNotificationSetting();
            setting.setUserId(userId);
            setting.setEmailOrderUpdate(1);
            setting.setEmailPromotion(1);
            setting.setSiteOrderUpdate(1);
            setting.setSiteLiveRemind(1);
            setting.setSitePromotion(1);
            setting.setSiteCoupon(1);
            settingMapper.insert(setting);
        }
        return setting;
    }

    private NotificationVo toVo(Notification n) {
        NotificationVo vo = new NotificationVo();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTypeLabel(NotificationType.fromCode(n.getType()).getLabel());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setTargetType(n.getTargetType());
        vo.setTargetId(n.getTargetId());
        vo.setIsRead(n.getIsRead());
        vo.setReadTime(n.getReadTime());
        vo.setCreateTime(n.getCreateTime());
        vo.setTimeAgo(formatTimeAgo(n.getCreateTime()));
        return vo;
    }

    /**
     * 人性化时间显示
     */
    private String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "";
        long minutes = ChronoUnit.MINUTES.between(time, LocalDateTime.now());
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        if (days < 7) return days + "天前";
        if (days < 30) return (days / 7) + "周前";
        return time.toLocalDate().toString();
    }
}
