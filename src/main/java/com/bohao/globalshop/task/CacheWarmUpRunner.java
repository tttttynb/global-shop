package com.bohao.globalshop.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
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


    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 这个 run 方法会在 Spring Boot 启动成功后的第一时间自动执行！
     */
    @Override
    public void run(String... args)throws Exception{
        log.info("🚀 ------------------------------------------");
        log.info("🚀 系统启动成功！正在执行 Redis 缓存预热...");

        // 1. 去数据库查出所有状态为 1 (已上架) 的商品
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        List<Product> productList = productMapper.selectList(qw);

        // 2. 遍历这些商品，将它们的最新库存提前塞进 Redis！
        for (Product product : productList) {
            // 构造咱们刚刚在 Lua 脚本里写死的那个 Key
            String stockKey = "seckill:stock:" + product.getId();

            // 写入 Redis，Value 必须是字符串格式的数字
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));

            log.info("✅ 预热商品库存完成：[{}] -> {}", stockKey, product.getStock());
        }

        log.info("🎉 缓存预热完毕！防超卖 Lua 护城河已准备就绪，欢迎高并发挑战！");
        log.info("🚀 ------------------------------------------");
    }
}
