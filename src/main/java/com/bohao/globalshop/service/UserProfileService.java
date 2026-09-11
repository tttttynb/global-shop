package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.UserProfile;

import java.math.BigDecimal;

/**
 * 用户消费画像服务
 * <p>
 * 负责画像的计算、分层判定、缓存管理和更新
 */
public interface UserProfileService {

    /**
     * 获取用户画像（优先从缓存读取）
     *
     * @param userId 用户ID
     * @return 用户画像，新用户返回默认空画像
     */
    UserProfile getProfile(Long userId);

    /**
     * 订单确认收货后实时更新画像
     * <p>
     * 更新内容：totalSpent、totalOrderCount、avgOrderValue、maxSingleOrder、
     * userTier、priceRange、lastOrderTime
     *
     * @param userId      用户ID
     * @param orderAmount 订单金额
     */
    void updateOnOrderComplete(Long userId, BigDecimal orderAmount);

    /**
     * 全量重算某个用户的画像（管理后台或数据修复用）
     *
     * @param userId 用户ID
     */
    void recalculateProfile(Long userId);

    /**
     * 批量更新所有用户的近90天指标和偏好品类
     * <p>
     * 每日凌晨定时执行
     */
    void batchUpdateRecentMetrics();

    /**
     * 根据消费指标判定用户层级
     *
     * @param profile 用户画像
     * @return 层级标签字符串
     */
    String classifyTier(UserProfile profile);

    /**
     * 为历史存量用户初始化画像（一次性脚本用）
     */
    void initProfilesForExistingUsers();
}
