package com.bohao.globalshop.interceptor;

import com.bohao.globalshop.common.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从 HTTP 请求的头部 (Header) 中获取通行证
        // 行业规范：通行证通常放在一个叫 "Authorization" 的头信息里
        String token = request.getHeader("Authorization");
        //2.如果没带通行证
        if (token == null || token.isEmpty()) {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":401,\"message\":\\\"不好意思，请先登录！\"}");
            return false;
        }
        //3.验证通行证的真伪
        Long userId = JwtUtils.verifyToken(token);
        if (userId == null) {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\": 401, \"message\": \"通行证无效或已过期，请重新登录！\"}");
            return false;
        }
        //4.验证通过。为了方便后面的业务代码（比如下单）知道是谁，我们把 userId 塞进请求里带过去
        request.setAttribute("currentUserId", userId);
        return true;
    }
}
