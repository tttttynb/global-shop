package com.bohao.globalshop.filter;

import com.bohao.globalshop.common.JwtUtils;
import com.bohao.globalshop.common.UserContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 用户上下文过滤器 — 在所有请求上尝试提取 JWT 中的 userId
 * <p>
 * 不做鉴权拦截（那是 JwtInterceptor 的职责），只做"尽力而为"的 userId 提取。
 * 如果请求带了有效 JWT → 设置 ThreadLocal userId
 * 如果没带或无效 → ThreadLocal 保持 null
 * <p>
 * 运行顺序：Filter（本类） → Interceptor（JwtInterceptor） → Controller
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE) // 最早执行，确保 Controller 中可用
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String token = httpRequest.getHeader("Authorization");
            if (token != null && !token.isEmpty()) {
                Long userId = JwtUtils.verifyToken(token);
                if (userId != null) {
                    UserContextHolder.setCurrentUserId(userId);
                }
            }
            chain.doFilter(request, response);
        } finally {
            // 请求结束后务必清理，防止内存泄漏
            UserContextHolder.clear();
        }
    }
}
