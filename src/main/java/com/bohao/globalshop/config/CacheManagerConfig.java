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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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

    @Bean
    public RBloomFilter<Long> productBloomFilter() {
        // 创建一个名为 "product:bloom:filter" 的布隆过滤器
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("product:bloom:filter");

        // 初始化布隆过滤器：预计存 10万 个数据，容错率为 0.01 (1%)
        bloomFilter.tryInit(100000L, 0.01);

        // 缓存预热核心：分页加载上架商品 ID，避免内存溢出
        int pageSize = 1000;
        int pageNum = 1;
        long totalLoaded = 0;

        while (true) {
            // 只查询上架商品（status=1），且只查询 id 字段
            LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                    .select(Product::getId)
                    .eq(Product::getStatus, 1);
            Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);

            List<Product> products = page.getRecords();
            if (products.isEmpty()) {
                break;
            }

            for (Product product : products) {
                bloomFilter.add(product.getId());
            }
            totalLoaded += products.size();

            // 如果当前页数据不足一页，说明已经是最后一页
            if (products.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        log.info("布隆过滤器初始化完成！已加载 {} 个上架商品 ID", totalLoaded);

        return bloomFilter;
    }

}
