package com.bohao.globalshop.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductSku;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.service.SkuService;
import com.bohao.globalshop.service.impl.SkuServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpRunner implements CommandLineRunner {

    private final ProductMapper productMapper;

    private final SkuService skuService;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 这个 run 方法会在 Spring Boot 启动成功后的第一时间自动执行！
     * 🆕 SKU 化改造：库存预热从商品维度细化到 SKU 维度（seckill:stock:sku:{skuId}），
     * 同时为存量无 SKU 的商品懒加载生成默认 SKU，并保留旧商品级 Key 向下兼容。
     */
    @Override
    public void run(String... args)throws Exception{
        log.info("🚀 ------------------------------------------");
        log.info("🚀 系统启动成功！正在执行 Redis 缓存预热（SKU 级）...");

        // 1. 去数据库查出所有状态为 1 (已上架) 的商品
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        List<Product> productList = productMapper.selectList(qw);

        // 2. 遍历商品：补齐默认 SKU → 逐个 SKU 预热库存
        int skuCount = 0;
        for (Product product : productList) {
            List<ProductSku> skus = skuService.listByProductId(product.getId());
            if (skus == null || skus.isEmpty()) {
                // 存量单规格商品：自动生成默认 SKU（is_default=1）
                ProductSku defaultSku = skuService.getOrCreateDefaultSku(product.getId());
                if (defaultSku != null) {
                    skus = List.of(defaultSku);
                } else {
                    continue;
                }
            }
            for (ProductSku sku : skus) {
                String skuStockKey = SkuServiceImpl.SKU_STOCK_KEY_PREFIX + sku.getId();
                stringRedisTemplate.opsForValue().set(skuStockKey, String.valueOf(sku.getStock()));
                skuCount++;
                log.info("✅ 预热 SKU 库存完成：[{}] {} -> {}", skuStockKey, sku.getSpecText(), sku.getStock());
            }
            // 3. 保留旧的商品级 Key（seckill:stock:{productId}），兼容尚未 SKU 化的调用方
            String stockKey = "seckill:stock:" + product.getId();
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
        }

        log.info("🎉 缓存预热完毕！共预热 {} 个 SKU，防超卖 Lua 护城河已准备就绪，欢迎高并发挑战！", skuCount);
        log.info("🚀 ------------------------------------------");
    }
}
