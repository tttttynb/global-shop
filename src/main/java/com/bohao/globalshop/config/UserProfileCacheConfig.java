package com.bohao.globalshop.config;

import com.bohao.globalshop.entity.UserProfile;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 用户画像缓存配置
 * <p>
 * Caffeine L1 本地缓存，TTL 30 分钟，最大 5000 条
 */
@Configuration
public class UserProfileCacheConfig {

    @Bean
    public Cache<Long, UserProfile> profileLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(5000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
