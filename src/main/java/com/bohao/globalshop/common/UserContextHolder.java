package com.bohao.globalshop.common;

/**
 * 用户上下文持有者 — ThreadLocal 实现
 * <p>
 * 在请求生命周期内，任何地方都能通过 {@code getCurrentUserId()} 获取当前用户 ID。
 * 如果用户未登录（无有效 JWT），则返回 null。
 * <p>
 * 由 {@link com.bohao.globalshop.filter.UserContextFilter} 在每个请求进入时设置，
 * 请求结束时自动清理。
 */
public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
