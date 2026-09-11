package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bohao.globalshop.dto.SkuDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductSku;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductSkuMapper;
import com.bohao.globalshop.service.SkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    /** SKU 级秒杀库存 Key 前缀（商品级旧 Key seckill:stock:{productId} 仍由预热任务写入，向下兼容） */
    public static final String SKU_STOCK_KEY_PREFIX = "seckill:stock:sku:";

    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillScript;

    @Override
    public List<ProductSku> listByProductId(Long productId) {
        QueryWrapper<ProductSku> qw = new QueryWrapper<>();
        qw.eq("product_id", productId)
                .eq("status", 1)
                .orderByAsc("id");
        return productSkuMapper.selectList(qw);
    }

    @Override
    public ProductSku getOrCreateDefaultSku(Long productId) {
        QueryWrapper<ProductSku> qw = new QueryWrapper<>();
        qw.eq("product_id", productId).eq("is_default", 1).last("LIMIT 1");
        ProductSku existing = productSkuMapper.selectOne(qw);
        if (existing != null) {
            return existing;
        }
        // 懒加载兼容：根据商品表的 price/stock 自动生成默认 SKU
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecJson("{}");
        sku.setSpecText("默认规格");
        sku.setPrice(product.getPrice());
        sku.setStock(product.getStock() != null ? product.getStock() : 0);
        sku.setImage(null);
        sku.setIsDefault(1);
        sku.setStatus(1);
        productSkuMapper.insert(sku);
        log.info("🔧 为存量商品 [{}] 自动创建默认 SKU [{}]", productId, sku.getId());
        return sku;
    }

    @Override
    public ProductSku resolveSku(Long productId, Long skuId) {
        if (skuId == null) {
            return getOrCreateDefaultSku(productId);
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !sku.getProductId().equals(productId) || sku.getStatus() != 1) {
            return null;
        }
        return sku;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceSkus(Long productId, List<SkuDto> skus) {
        // 1. 🆕 按 ID 原地更新（编辑场景回传 id）：保持 SKU ID 不变，购物车/历史订单引用的规格不会失效
        List<ProductSku> existing = listByProductId(productId);
        Map<Long, ProductSku> existingById = new HashMap<>();
        for (ProductSku sku : existing) {
            existingById.put(sku.getId(), sku);
        }
        Set<Long> keptIds = new HashSet<>();
        for (SkuDto dto : skus) {
            ProductSku sku = dto.getId() != null ? existingById.get(dto.getId()) : null;
            if (sku == null) {
                sku = new ProductSku();
                sku.setProductId(productId);
                sku.setStatus(1);
            }
            if (dto.getSpecJson() != null) {
                sku.setSpecJson(dto.getSpecJson());
            } else if (sku.getSpecJson() == null) {
                sku.setSpecJson("{}");
            }
            sku.setSpecText(dto.getSpecText() != null ? dto.getSpecText() : "默认规格");
            sku.setPrice(dto.getPrice());
            sku.setStock(dto.getStock() != null ? dto.getStock() : 0);
            sku.setImage(dto.getImage());
            if (sku.getId() == null) {
                productSkuMapper.insert(sku);
            } else {
                productSkuMapper.updateById(sku);
                keptIds.add(sku.getId());
            }
        }
        // 2. 删除本次未提交的旧 SKU（商家删掉了该规格），并清理其 Redis 库存 Key
        for (ProductSku sku : existing) {
            if (!keptIds.contains(sku.getId())) {
                productSkuMapper.deleteById(sku.getId());
                stringRedisTemplate.delete(SKU_STOCK_KEY_PREFIX + sku.getId());
            }
        }
        // 3. 同步商品聚合字段 + 刷新 Redis 库存 + 维护默认标记
        List<ProductSku> finalSkus = listByProductId(productId);
        int defaultFlag = finalSkus.size() == 1 ? 1 : 0;
        for (ProductSku sku : finalSkus) {
            if (sku.getIsDefault() == null || sku.getIsDefault() != defaultFlag) {
                sku.setIsDefault(defaultFlag);
                productSkuMapper.updateById(sku);
            }
            stringRedisTemplate.opsForValue().set(SKU_STOCK_KEY_PREFIX + sku.getId(), String.valueOf(sku.getStock()));
        }
        syncProductAggregate(productId);
    }

    @Override
    public void removeByProductId(Long productId) {
        QueryWrapper<ProductSku> qw = new QueryWrapper<>();
        qw.eq("product_id", productId);
        productSkuMapper.delete(qw);
    }

    @Override
    public void syncProductAggregate(Long productId) {
        List<ProductSku> skus = listByProductId(productId);
        if (skus == null || skus.isEmpty()) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return;
        }
        BigDecimal minPrice = skus.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(product.getPrice());
        int totalStock = skus.stream().mapToInt(s -> s.getStock() != null ? s.getStock() : 0).sum();
        product.setPrice(minPrice);
        product.setStock(totalStock);
        productMapper.updateById(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyProductLevelChange(Long productId, BigDecimal price, Integer stock) {
        List<ProductSku> skus = listByProductId(productId);
        if (skus.isEmpty()) {
            // 无 SKU：商品表本身就是真相源，调用方已更新，无需下发
            return;
        }
        if (price != null) {
            for (ProductSku sku : skus) {
                sku.setPrice(price);
                productSkuMapper.updateById(sku);
            }
        }
        if (stock != null) {
            int total = skus.stream().mapToInt(s -> s.getStock() != null ? s.getStock() : 0).sum();
            int assigned = 0;
            for (int i = 0; i < skus.size(); i++) {
                ProductSku sku = skus.get(i);
                int share;
                if (i == skus.size() - 1) {
                    share = stock - assigned; // 末位 SKU 吸收余数，保证总和精确等于目标库存
                } else if (total > 0) {
                    share = stock * (sku.getStock() != null ? sku.getStock() : 0) / total; // 按比例分配，保留售罄态
                } else {
                    share = stock / skus.size();
                }
                sku.setStock(share);
                assigned += share;
                productSkuMapper.updateById(sku);
            }
        }
        // 重算商品聚合字段 + 刷新 Redis 库存（与 replaceSkus 保持一致）
        syncProductAggregate(productId);
        for (ProductSku sku : listByProductId(productId)) {
            stringRedisTemplate.opsForValue().set(SKU_STOCK_KEY_PREFIX + sku.getId(), String.valueOf(sku.getStock()));
        }
    }

    @Override
    public boolean deductStock(Long skuId, int quantity) {
        // 原子扣减：UPDATE product_sku SET stock = stock - N WHERE id = ? AND stock >= N
        UpdateWrapper<ProductSku> uw = new UpdateWrapper<>();
        uw.eq("id", skuId)
                .ge("stock", quantity)
                .setSql("stock = stock - " + quantity);
        return productSkuMapper.update(null, uw) > 0;
    }

    @Override
    public void restoreStock(Long skuId, int quantity) {
        UpdateWrapper<ProductSku> uw = new UpdateWrapper<>();
        uw.eq("id", skuId)
                .setSql("stock = stock + " + quantity);
        productSkuMapper.update(null, uw);
    }

    @Override
    public void ensureRedisStock(Long skuId) {
        String key = SKU_STOCK_KEY_PREFIX + skuId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (exists != null && exists) {
            return;
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            return;
        }
        // setIfAbsent 防止并发覆盖已被扣减的库存
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(sku.getStock()));
    }

    @Override
    public boolean deductRedisStock(Long skuId, int quantity) {
        ensureRedisStock(skuId);
        Long luaResult = stringRedisTemplate.execute(
                seckillScript,
                Collections.singletonList(SKU_STOCK_KEY_PREFIX + skuId),
                String.valueOf(quantity)
        );
        return luaResult != null && luaResult == 1L;
    }

    @Override
    public void restoreRedisStock(Long skuId, int quantity) {
        String key = SKU_STOCK_KEY_PREFIX + skuId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (exists != null && exists) {
            stringRedisTemplate.opsForValue().increment(key, quantity);
        }
    }
}
