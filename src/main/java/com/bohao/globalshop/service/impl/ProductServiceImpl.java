package com.bohao.globalshop.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.AiReviewSummaryDto;
import com.bohao.globalshop.dto.FrequentlyBoughtDto;
import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductFavorite;
import com.bohao.globalshop.entity.ProductReview;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.mapper.ProductFavoriteMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductReviewMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.service.ProductService;
import com.bohao.globalshop.vo.ProductReviewVo;
import com.bohao.globalshop.vo.ProductVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final ProductFavoriteMapper productFavoriteMapper;
    private final ShopMapper shopMapper;
    private final ProductReviewMapper productReviewMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<Long, String> productLocalCache;
    private final RBloomFilter<Long> productBloomFilter;
    private final RedissonClient redissonClient;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations elasticsearchOperations;


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
    public Result<Map<String, Object>> getProductListPaged(Long categoryId, String sort, Integer page, Integer size) {
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        if ("price_asc".equals(sort)) {
            qw.orderByAsc("price");
        } else if ("price_desc".equals(sort)) {
            qw.orderByDesc("price");
        } else if ("sales".equals(sort)) {
            qw.orderByDesc("sales_count");
        } else {
            qw.orderByDesc("create_time");
        }

        long total = productMapper.selectCount(qw);
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + "," + size);
        List<Product> products = productMapper.selectList(qw);

        Set<Long> shopIds = products.stream()
                .map(Product::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Shop> shopMap = shopIds.isEmpty()
                ? Map.of()
                : shopMapper.selectBatchIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Function.identity()));

        List<ProductVo> voList = new ArrayList<>();
        for (Product product : products) {
            ProductVo vo = new ProductVo();
            vo.setId(product.getId());
            vo.setShopId(product.getShopId());
            vo.setName(product.getName());
            vo.setDescription(product.getDescription());
            vo.setPrice(product.getPrice());
            vo.setStock(product.getStock());
            vo.setCoverImage(product.getCoverImage());
            Shop shop = product.getShopId() != null ? shopMap.get(product.getShopId()) : null;
            vo.setShopName(shop != null ? shop.getName() : "平台自营店");
            voList.add(vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", voList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
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

    @Override
    public Result<String> toggleFavorite(Long userId, Long productId) {
        QueryWrapper<ProductFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("product_id", productId);
        ProductFavorite existing = productFavoriteMapper.selectOne(qw);
        if (existing != null) {
            productFavoriteMapper.deleteById(existing.getId());
            return Result.success("已取消收藏");
        } else {
            ProductFavorite fav = new ProductFavorite();
            fav.setUserId(userId);
            fav.setProductId(productId);
            fav.setCreateTime(java.time.LocalDateTime.now());
            productFavoriteMapper.insert(fav);
            return Result.success("已收藏");
        }
    }

    @Override
    public Result<List<ProductVo>> getFavorites(Long userId) {
        QueryWrapper<ProductFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("create_time");
        List<ProductFavorite> favList = productFavoriteMapper.selectList(qw);
        List<ProductVo> voList = new ArrayList<>();
        for (ProductFavorite fav : favList) {
            Product product = productMapper.selectById(fav.getProductId());
            if (product != null) {
                ProductVo vo = new ProductVo();
                vo.setId(product.getId());
                vo.setShopId(product.getShopId());
                vo.setName(product.getName());
                vo.setDescription(product.getDescription());
                vo.setPrice(product.getPrice());
                vo.setStock(product.getStock());
                vo.setCoverImage(product.getCoverImage());
                Shop shop = product.getShopId() != null ? shopMapper.selectById(product.getShopId()) : null;
                vo.setShopName(shop != null ? shop.getName() : "平台自营店");
                voList.add(vo);
            }
        }
        return Result.success(voList);
    }

    // ==================== AI 评价总结（Tier 1.1） ====================

    private static final int MIN_REVIEWS_FOR_AI = 3;
    private static final String AI_SUMMARY_KEY_PREFIX = "ai:review:summary:";

    @Override
    public Result<AiReviewSummaryDto> getAiReviewSummary(Long productId) {
        // 1. 查缓存
        String cacheKey = AI_SUMMARY_KEY_PREFIX + productId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Result.success(objectMapper.readValue(cached, AiReviewSummaryDto.class));
            } catch (Exception e) {
                log.warn("AI 评价缓存反序列化失败，重新生成: {}", e.getMessage());
            }
        }

        // 2. 查评论
        QueryWrapper<ProductReview> qw = new QueryWrapper<>();
        qw.eq("product_id", productId);
        qw.orderByDesc("create_time");
        List<ProductReview> reviews = productReviewMapper.selectList(qw);

        if (reviews.size() < MIN_REVIEWS_FOR_AI) {
            return Result.success(null);
        }

        // 3. 构建 Prompt
        StringBuilder reviewText = new StringBuilder();
        for (ProductReview r : reviews) {
            reviewText.append("- [评分: ").append(r.getRating()).append("/5] ");
            reviewText.append(r.getContent() != null ? r.getContent() : "（无文字）");
            reviewText.append("\n");
        }

        String prompt = """
                你是一个专业的电商评价分析师。请根据以下商品评价，生成 JSON 格式的总结。

                要求：
                1. pros: 提炼 3-5 个优点（每条 15 字以内）
                2. cons: 提炼 2-4 个缺点（每条 15 字以内，如果无明显缺点则写"暂无明显缺点"）
                3. bestFor: 一句话描述适合什么人群（20 字以内）
                4. aiRating: 基于评价内容的综合评分（0-5，保留 1 位小数）
                5. summary: 整体总结（80 字以内）

                只返回 JSON，不要 markdown 代码块标记：
                {"pros":["...",..."], "cons":["...",..."], "bestFor":"...", "aiRating":4.2, "summary":"..."}

                === 商品评价（共 %d 条） ===
                %s
                """.formatted(reviews.size(), reviewText.toString());

        // 4. 调 LLM
        try {
            String llmResponse = chatModel.chat(UserMessage.from(prompt)).aiMessage().text();
            log.debug("AI 评价总结原始响应: {}", llmResponse);

            // 5. 清理可能的 markdown 标记
            String json = llmResponse.trim();
            if (json.startsWith("```json")) json = json.substring(7);
            if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();

            AiReviewSummaryDto dto = objectMapper.readValue(json, AiReviewSummaryDto.class);
            dto.setReviewCount(reviews.size());

            // 6. 写缓存（24h）
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(dto), 24, TimeUnit.HOURS);

            return Result.success(dto);
        } catch (Exception e) {
            log.error("AI 评价总结生成失败: productId={}", productId, e);
            return Result.error(500, "AI 总结生成失败，请稍后重试");
        }
    }

    // ==================== "看了还看" — ES More Like This（Tier 2.1a） ====================

    @Override
    public Result<List<ProductVo>> getSimilarProducts(Long productId, int size) {
        try {
            // 1. 查 MySQL 获取商品名称，作为 MLT 的 like 文本
            Product product = productMapper.selectById(productId);
            if (product == null || product.getName() == null) {
                return Result.success(List.of());
            }

            // 2. 构建 ES More Like This 查询，排除自身
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.moreLikeThis(mlt -> mlt
                                    .fields("name")
                                    .like(l -> l.text(product.getName()))
                                    .minTermFreq(1)
                                    .minDocFreq(1)
                                    .maxQueryTerms(12)
                            ))
                            .mustNot(mn -> mn.ids(i -> i.values(String.valueOf(productId))))
                    ))
                    .withMaxResults(size)
                    .build();

            SearchHits<EsProduct> hits = elasticsearchOperations.search(query, EsProduct.class);

            // 3. 提取结果，批量查店铺名称
            List<Long> productIds = new ArrayList<>();
            for (SearchHit<EsProduct> hit : hits) {
                EsProduct ep = hit.getContent();
                if (ep.getId() != null) {
                    productIds.add(ep.getId());
                }
            }

            if (productIds.isEmpty()) {
                return Result.success(List.of());
            }

            List<Product> products = productMapper.selectBatchIds(productIds);
            Set<Long> shopIds = products.stream()
                    .map(Product::getShopId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, Shop> shopMap = shopIds.isEmpty()
                    ? Map.of()
                    : shopMapper.selectBatchIds(shopIds).stream()
                            .collect(Collectors.toMap(Shop::getId, Function.identity()));

            // 保持 ES 返回的排序
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            List<ProductVo> voList = new ArrayList<>();
            for (Long pid : productIds) {
                Product p = productMap.get(pid);
                if (p != null) {
                    ProductVo vo = new ProductVo();
                    vo.setId(p.getId());
                    vo.setShopId(p.getShopId());
                    vo.setName(p.getName());
                    vo.setDescription(p.getDescription());
                    vo.setPrice(p.getPrice());
                    vo.setStock(p.getStock());
                    vo.setCoverImage(p.getCoverImage());
                    Shop shop = p.getShopId() != null ? shopMap.get(p.getShopId()) : null;
                    vo.setShopName(shop != null ? shop.getName() : "平台自营店");
                    voList.add(vo);
                }
            }

            return Result.success(voList);
        } catch (Exception e) {
            log.error("ES More Like This 查询失败: productId={}", productId, e);
            return Result.success(List.of());
        }
    }

    // ==================== "买了还买" — 订单共现矩阵（Tier 2.1b） ====================

    @Override
    public Result<List<FrequentlyBoughtDto>> getFrequentlyBought(Long productId, int size) {
        try {
            String zsetKey = "frequently:bought:" + productId;
            // ZREVRANGE frequently:bought:{productId} 0 {size-1} WITHSCORES
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> topItems =
                    stringRedisTemplate.opsForZSet().reverseRangeWithScores(zsetKey, 0, size - 1);

            if (topItems == null || topItems.isEmpty()) {
                return Result.success(List.of());
            }

            // 提取 productId 和 score
            List<Long> relatedIds = new ArrayList<>();
            Map<Long, Integer> scoreMap = new HashMap<>();
            for (var item : topItems) {
                Long rid = Long.valueOf(item.getValue());
                relatedIds.add(rid);
                scoreMap.put(rid, item.getScore() != null ? item.getScore().intValue() : 0);
            }

            // 批量查商品
            List<Product> products = productMapper.selectBatchIds(relatedIds);
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            List<FrequentlyBoughtDto> result = new ArrayList<>();
            for (Long rid : relatedIds) {
                Product p = productMap.get(rid);
                if (p != null) {
                    FrequentlyBoughtDto dto = new FrequentlyBoughtDto();
                    dto.setId(p.getId());
                    dto.setName(p.getName());
                    dto.setPrice(p.getPrice());
                    dto.setCoverImage(p.getCoverImage());
                    dto.setCoOccurrenceCount(scoreMap.getOrDefault(rid, 0));
                    result.add(dto);
                }
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("共现推荐查询失败: productId={}", productId, e);
            return Result.success(List.of());
        }
    }
}
