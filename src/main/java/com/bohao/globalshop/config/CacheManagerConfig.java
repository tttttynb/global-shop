package com.bohao.globalshop.config;


import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheManagerConfig {
    private final RedissonClient redissonClient;
    private final ProductMapper productMapper;

    // 1. 配置 Caffeine 本地缓存 (L1 缓存)
    @Bean
    public Cache<Long, String> productLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    // 2. 配置布隆过滤器 (防止缓存穿透)
    @Bean
    public RBloomFilter<Long> productBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(
                "product:bloom:filter"
        );
        bloomFilter.tryInit(100000L, 0.01);
        
        return bloomFilter;
    }

    // 3. 初始化布隆过滤器数据
    @PostConstruct
    public void initBloomFilterData() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("product:bloom:filter");
        List<Product> products = productMapper.selectList(null);
        for (Product product : products) {
            bloomFilter.add(product.getId());
        }
        log.info("🛡️ 布隆过滤器初始化完成！已加载 {} 个真实商品 ID。", products.size());
    }

}
