package com.bohao.globalshop.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductReview;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductReviewMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.service.ProductService;
import com.bohao.globalshop.vo.ProductReviewVo;
import com.bohao.globalshop.vo.ProductVo;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final ProductReviewMapper productReviewMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<Long, String> productLocalCache;
    private final RBloomFilter<Long> productBloomFilter;
    private final RedissonClient redissonClient;


    @Override
    public Result<List<ProductVo>> getProductListWithShop() {
        // 1. 先查出所有上架状态(status=1)的商品
        QueryWrapper<Product> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        List<Product> products = productMapper.selectList(queryWrapper);

        // 2. 批量查询优化：收集所有不重复的 shopId，一次性查询所有店铺
        Set<Long> shopIds = products.stream()
                .map(Product::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 批量查询店铺，转换为 Map 方便快速查找
        Map<Long, Shop> shopMap = shopIds.isEmpty()
                ? Map.of()
                : shopMapper.selectBatchIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Function.identity()));

        // 4. 组装 VO 列表
        ArrayList<ProductVo> voList = new ArrayList<>();
        for (Product product : products) {
            ProductVo vo = new ProductVo();
            vo.setId(product.getId());
            vo.setShopId(product.getShopId());
            vo.setName(product.getName());
            vo.setDescription(product.getDescription());
            vo.setPrice(product.getPrice());
            vo.setStock(product.getStock());
            vo.setCoverImage(product.getCoverImage());

            // 从 Map 中获取店铺信息，避免 N+1 查询
            Shop shop = product.getShopId() != null ? shopMap.get(product.getShopId()) : null;
            if (shop != null) {
                vo.setShopName(shop.getName());
            } else {
                vo.setShopName("平台自营店");
            }
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<List<ProductReviewVo>> getProductReviews(Long productId) {
        QueryWrapper<ProductReview> qw = new QueryWrapper<>();
        qw.eq("product_id", productId);
        qw.orderByDesc("create_time");
        List<ProductReview> reviews = productReviewMapper.selectList(qw);
        List<ProductReviewVo> voList = new ArrayList<>();
        for (ProductReview review : reviews) {
            ProductReviewVo vo = new ProductReviewVo();
            vo.setId(review.getId());
            vo.setRating(review.getRating());
            vo.setContent(review.getContent());
            vo.setImages(review.getImages());
            vo.setCreateTime(review.getCreateTime());

            // 4. 🚨 大厂核心微操：去 user 表查出买家信息，并进行“隐私脱敏”！
            User user = userMapper.selectById(review.getUserId());
            if (user != null) {
                String name = user.getUsername();
                if (name != null && name.length() > 1) {
                    // 脱敏算法：只保留头尾字符，中间全部换成 ***
                    vo.setUsername(name.charAt(0) + "***" + name.charAt(name.length() - 1));
                } else {
                    vo.setUsername("匿***名");
                }
                vo.setUserAvatar("https://api.dicebear.com/7.x/adventurer/svg?seed=" + user.getUsername());
            } else {
                vo.setUsername("已注销用户");
            }
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Product getProductDetail(Long productId) {
        // 防御一：【缓存穿透】拦截！黑客用不存在的 ID (比如 -1) 疯狂攻击
        if (!productBloomFilter.contains(productId)) {
            throw new RuntimeException("商品不存在，请勿恶意请求！");
        }
        // 第一级缓存 (L1)：Caffeine 本地内存
        // 速度极快 (纳秒级)，完全不需要网络传输
        String localCacheData = productLocalCache.getIfPresent(productId);
        if (localCacheData != null) {
            System.out.println("命中 L1 Caffeine 本地缓存！");
            return JSONUtil.toBean(localCacheData, Product.class); // 字符串转回对象
        }
        // 第二级缓存 (L2)：Redis 分布式缓存
        String redisKey = "product:detail:" + productId;
        String redisData = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisData != null) {
            log.info("🚀 命中 L2 Redis 分布式缓存！");
            // 查到数据后，顺手塞回 L1 本地缓存，方便下次极速读取
            productLocalCache.put(productId, redisData);
            return JSONUtil.toBean(redisData, Product.class);
        }
        // 防御二：【缓存击穿】防御！热点商品(比如茅台)在 Redis 突然过期
        // 如果 10 万人同时走到这里，不能让他们全部去查 MySQL，必须用锁拦住 99999 个人！
        RLock lock = redissonClient.getLock("kock:product:detail" + productId);
        try {
            // 尝试加锁：最多等待 3秒，拿到锁后 10秒 自动释放
            boolean isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (isLocked) {
                // 拿到锁的，必须再查一次 Redis (极其关键的 Double-Check)！
                // 因为可能前面刚释放锁的人，已经把数据写进 Redis 了
                redisData = stringRedisTemplate.opsForValue().get(redisKey);
                if (redisData != null) {
                    productLocalCache.put(productId, redisData);
                    return JSONUtil.toBean(redisData, Product.class);
                }
                //查询 MySQL 数据库
                log.info("缓存全部未命中，查询 MySQL 数据库");
                Product productFromDb = productMapper.selectById(productId);
                if (productFromDb != null) {
                    String jsonStr = JSONUtil.toJsonStr(productFromDb);
                    // ️ 防御三：【缓存雪崩】防御！大批商品同时过期
                    // 给过期时间加上一个随机数，防止大家在同一秒“集体自杀”
                    int randomMinutes = new Random().nextInt(30);//0-30分钟的随机数
                    long expireTime = 60 + randomMinutes;
                    // 写入 L2 (Redis) 并设置随机过期时间
                    stringRedisTemplate.opsForValue().set(redisKey, jsonStr, expireTime, TimeUnit.MINUTES);
                    // 写入 L1 (Caffeine)
                    productLocalCache.put(productId, jsonStr);
                }
                return productFromDb;
            } else {
                // 没抢到锁的人，说明已经有线程在努力查数据库了，稍微睡个 50 毫秒，然后重试！
                Thread.sleep(50);
                return getProductDetail(productId);//递归重试
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("系统繁忙，请稍后再试！");
        } finally {
            // 释放锁 (必须要判断是不是自己加的锁)
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
