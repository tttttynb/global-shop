package com.bohao.globalshop.config;

import com.bohao.globalshop.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@RequiredArgsConstructor
@Configuration// 告诉 Spring 这是一个配置类，启动时要加载它
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 安排保安守住所有以 /api/order/ 开头的接口（比如未来的下单接口）
                .addPathPatterns("/api/order/**", "/api/cart/**", "/api/merchant/**", "/api/chat/**")
                // 同时，告诉保安不要去管登录、注册和查看商品列表的接口
                .excludePathPatterns("/api/user/login", "/api/user/register", "/api/product/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有接口
                // TODO: 生产环境需要限制为具体的前端域名，如 "https://www.example.com"
                .allowedOriginPatterns("*") // 开发阶段允许所有前端地址
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 限制允许的请求方式
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With") // 限制允许的请求头
                .allowCredentials(true); // 允许携带凭证（如 Cookie/Token）
    }

}

