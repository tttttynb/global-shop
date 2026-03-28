package com.bohao.globalshop.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 配置单节点模式 (SingleServer)
        // 注意：地址必须以 redis:// 开头
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setDatabase(0); // 默认使用 0 号库

        // 🚨 如果你的 Redis 设置了密码，请把下面这行的注释解开，并填入你的密码！
        // config.useSingleServer().setPassword("你的密码");

        return Redisson.create(config);
    }

    @Bean
    public DefaultRedisScript<Long> seckillScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);//返回1成功，0失败
        //核心Lua逻辑
        //1，获取当前Redis中的库存值
        //2.如果库存为空，或者库存小于购买数量，直接返回0（拦截）
        //3.如果库存充足，执行decrby扣减库存，返回1（放行）
        script.setScriptText(
                "local stock = tonumber(redis.call('get', KEYS[1])) \n" +
                        "if (stock == nil or stock < tonumber(ARGV[1])) then \n" +
                        "   return 0 \n" +
                        "end \n" +
                        "redis.call('decrby', KEYS[1], ARGV[1]) \n" +
                        "return 1 \n"
        );
        return script;
    }
}
