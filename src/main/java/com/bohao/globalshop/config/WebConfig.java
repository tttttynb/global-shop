package com.bohao.globalshop.config;

import com.bohao.globalshop.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration// 告诉 Spring 这是一个配置类，启动时要加载它
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 安排保安守住所有以 /api/order/ 开头的接口（比如未来的下单接口）
                .addPathPatterns("/api/order/**")
                // 同时，告诉保安不要去管登录、注册和查看商品列表的接口
                .excludePathPatterns("/api/user/login", "/api/user/register", "/api/product/list");
    }
}
