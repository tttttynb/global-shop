package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.bohao.globalshop.dto.MerchantDeliverDto;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.enums.NotificationTargetType;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.enums.ShipmentStatus;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.MerchantService;
import com.bohao.globalshop.vo.OrderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {
    public final ShopMapper shopMapper;
    public final ProductMapper productMapper;
    public final TraderOrderMapper traderOrderMapper;
    public final TradeOrderItemMapper tradeOrderItemMapper;
    public final RefundOrderMapper refundOrderMapper;
    public final UserProfileMapper userProfileMapper;
    public final CouponMapper couponMapper;
    public final UserCouponMapper userCouponMapper;
    public final StringRedisTemplate stringRedisTemplate;
    public final ShipmentMapper shipmentMapper;
    public final ApplicationEventPublisher eventPublisher;
    public final com.bohao.globalshop.service.SkuService skuService;
    public final com.bohao.globalshop.service.PriceHistoryService priceHistoryService;
    public final RBloomFilter<Long> productBloomFilter;
    public final Cache<Long, String> productLocalCache;

    /**
     * 🆕 缓存一致性：商品写操作后必须双删缓存（L1 Caffeine + L2 Redis）！
     * 详情页走两级缓存（Redis TTL 60+ 分钟），不删的话改价/上下架后用户看到的还是旧数据。
     */
    private void evictProductDetailCache(Long productId) {
        productLocalCache.invalidate(productId);
        stringRedisTemplate.delete("product:detail:" + productId);
    }


    @Override
    public Result<String> applyShop(Long userId, ShopApplyDto dto) {
        // 1. 校验：是不是已经开过店了？
        QueryWrapper<Shop> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        Shop existShop = shopMapper.selectOne(queryWrapper);
        if (existShop != null) {
            return Result.error(400, "您已经拥有一家店铺了，请勿重复申请！");
        }
        // 2. 创建新店铺
        Shop shop = new Shop();
        shop.setUserId(userId);
        shop.setName(dto.getName());
        shop.setDescription(dto.getDescription());
        // 为了咱们 MVP 测试方便，申请直接秒通过 (状态 1：营业中)！
        // 真实的平台这里一般是 0 (审核中)，需要后台管理员点通过。
        shop.setStatus(1);
        shopMapper.insert(shop);
        return Result.success("🎉 恭喜老板，您的店铺【" + shop.getName() + "】开张大吉！");
    }

    @Override
    public Result<String> publishProduct(Long userId, ProductPublishDto dto) {
        //1. 核心校验：先查出这个用户的店铺！
        QueryWrapper<Shop> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        Shop myShop = shopMapper.selectOne(queryWrapper);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺，无法上架商品哦！");
        }
        if (myShop.getStatus() != 1) {
            return Result.error(403, "您的店铺目前处于非营业状态，无法上架商品！");
        }
        //2. 上架商品：把生成的商品和当前用户的 shopId 死死绑定！
        Product product = new Product();
        product.setShopId(myShop.getId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCoverImage(dto.getCoverImage());
        product.setStatus(1);
        productMapper.insert(product);
        // 🆕 防缓存穿透：新商品 ID 同步进布隆过滤器（过滤器只在启动时预热）
        // 否则不加这一步，详情页 contains() 判不存在，会报“商品不存在，请勿恶意请求！”
        productBloomFilter.add(product.getId());
        //3. 🆕 SKU 规格系统：多规格整体落库并同步聚合字段；单规格自动生成默认 SKU
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            skuService.replaceSkus(product.getId(), dto.getSkus());
            // replaceSkus 已重算 price=最低SKU价 / stock=SKU库存之和，回读最新值
            product = productMapper.selectById(product.getId());
        } else {
            skuService.getOrCreateDefaultSku(product.getId());
        }
        //4. 🆕 价格历史：落一条初始价格快照（异步）
        priceHistoryService.recordPrice(product.getId(), product.getPrice());
        return Result.success("商品【" + product.getName() + "】上架成功！快去商城看看吧！");
    }

    @Override
    public Result<List<OrderVo>> getShopOrders(Long userId) {
        // 1. 查出当前老板的店铺
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop myShop = shopMapper.selectOne(shopQw);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        // 2.数据隔离查询：只查 trade_order 表上 shop_id 是自己店铺的订单！
        QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
        orderQw.eq("shop_id", myShop.getId());
        orderQw.orderByDesc("create_time");
        List<TradeOrder> myShopOrders = traderOrderMapper.selectList(orderQw);
        // 3. 组装 VO (带上子商品明细) 返回给前端
        List<OrderVo> voList = new ArrayList<>();
        for (TradeOrder order : myShopOrders) {
            OrderVo vo = new OrderVo();
            vo.setId(order.getId());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setDiscountAmount(order.getDiscountAmount());
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverPhone(order.getReceiverPhone());
            vo.setReceiverAddress(order.getReceiverAddress());
            vo.setCreateTime(order.getCreateTime());
            vo.setPaymentType(order.getPaymentType());
            vo.setPaymentId(order.getPaymentId());
            vo.setPayTime(order.getPayTime());
            vo.setCarrierName(order.getCarrierName());
            vo.setTrackingNumber(order.getTrackingNumber());
            vo.setShippedAt(order.getShippedAt());
            // 查出这个订单买了啥商品
            QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
            itemQw.eq("order_id", order.getId());
            vo.setItems(tradeOrderItemMapper.selectList(itemQw));
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<String> deliverOrder(Long userId, MerchantDeliverDto dto) {
        // 0. 参数校验
        if (dto.getCarrierName() == null || dto.getCarrierName().trim().isEmpty()) {
            return Result.error(400, "请选择物流公司！");
        }
        if (dto.getTrackingNumber() == null || dto.getTrackingNumber().trim().isEmpty()) {
            return Result.error(400, "请填写运单号！");
        }

        // 1. 查出当前老板的店铺
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop myShop = shopMapper.selectOne(shopQw);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }

        // 2.查出这笔订单
        TradeOrder order = traderOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            return Result.error(404, "找不到该订单！");
        }

        // 3.核心风控：越权校验
        if (!order.getShopId().equals(myShop.getId())) {
            return Result.error(403, "严重警告：越权操作！您不能发别人店铺的货！");
        }

        // 4. 状态校验
        if (order.getStatus() == 0) {
            return Result.error(400, "买家还没付钱呢，不能发货哦！");
        } else if (order.getStatus() == 2) {
            return Result.error(400, "订单已超时取消，无法发货！");
        } else if (order.getStatus() == 3) {
            return Result.error(400, "该订单已经发过货啦！");
        }

        // 5. 创建物流记录
        Shipment shipment = new Shipment();
        shipment.setOrderId(dto.getOrderId());
        shipment.setCarrierName(dto.getCarrierName());
        shipment.setCarrierCode(dto.getCarrierCode() != null ? dto.getCarrierCode() : "");
        shipment.setTrackingNumber(dto.getTrackingNumber());
        shipment.setStatus(ShipmentStatus.PENDING_PICKUP.getCode());
        shipment.setCurrentLocation("等待揽收");
        shipment.setEstimatedDelivery(java.time.LocalDate.now().plusDays(5));

        // 生成初始轨迹JSON
        String initialTracking = "[{\"time\":\"" + LocalDateTime.now().toString() + "\","
                + "\"status\":\"已发货\","
                + "\"location\":\"商家仓库\","
                + "\"description\":\"【" + dto.getCarrierName() + "】商家已发货，等待快递员揽收\"}]";
        shipment.setTrackingData(initialTracking);
        shipmentMapper.insert(shipment);

        // 6. 更新订单状态和物流字段
        order.setStatus(3);
        order.setCarrierName(dto.getCarrierName());
        order.setTrackingNumber(dto.getTrackingNumber());
        order.setShippedAt(LocalDateTime.now());
        traderOrderMapper.updateById(order);

        log.info("发货成功: orderId={}, carrier={}, trackingNumber={}",
                dto.getOrderId(), dto.getCarrierName(), dto.getTrackingNumber());

        // 发送通知给买家
        eventPublisher.publishEvent(new NotificationEvent(this,
                order.getUserId(),
                NotificationType.ORDER_STATUS.getCode(),
                "订单已发货",
                "您的订单 #" + order.getId() + " 已由【" + dto.getCarrierName() + "】揽收发货，运单号：" + dto.getTrackingNumber() + "，请留意签收。",
                NotificationTargetType.ORDER.getCode(),
                order.getId()));

        return Result.success("🚀 发货成功！" + dto.getCarrierName() + " 运单号：" + dto.getTrackingNumber());
    }

    @Override
    public Result<List<Product>> getMerchantProducts(Long userId) {
        Shop myShop = getMyShop(userId);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("shop_id", myShop.getId()).orderByDesc("create_time");
        return Result.success(productMapper.selectList(qw));
    }

    @Override
    public Result<String> updateProduct(Long userId, Long productId, ProductPublishDto dto) {
        Shop myShop = getMyShop(userId);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        Product product = productMapper.selectById(productId);
        if (product == null || !product.getShopId().equals(myShop.getId())) {
            return Result.error(403, "商品不存在或无权操作！");
        }
        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getStock() != null) product.setStock(dto.getStock());
        if (dto.getCoverImage() != null) product.setCoverImage(dto.getCoverImage());
        productMapper.updateById(product);
        // 🆕 SKU 规格系统：提交了 skus 则整体替换并重算聚合字段（price/stock 以 SKU 为准）
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            skuService.replaceSkus(productId, dto.getSkus());
        } else if (skuService.listByProductId(productId).isEmpty()) {
            // 存量商品第一次被编辑时补齐默认 SKU
            skuService.getOrCreateDefaultSku(productId);
        } else if (dto.getPrice() != null || dto.getStock() != null) {
            // 🆕 简易编辑对话框没有逐 SKU 输入：把商品级改价/改库存下发到全部 SKU。
            // 否则 SKU（真正的成交价来源）还是旧价，详情页/下单照旧，且下次聚合同步会把商品表改回去！
            skuService.applyProductLevelChange(productId, dto.getPrice(), dto.getStock());
        }
        // 🆕 价格历史：价格有变化时异步落一条快照（供走势图展示）
        Product latest = productMapper.selectById(productId);
        priceHistoryService.recordPrice(productId, latest.getPrice());
        // 🆕 缓存一致性：双删两级缓存，保证详情页立刻看到新价格
        evictProductDetailCache(productId);
        return Result.success("商品信息更新成功！");
    }

    @Override
    public Result<String> toggleProductStatus(Long userId, Long productId, Integer status) {
        Shop myShop = getMyShop(userId);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        Product product = productMapper.selectById(productId);
        if (product == null || !product.getShopId().equals(myShop.getId())) {
            return Result.error(403, "商品不存在或无权操作！");
        }
        product.setStatus(status);
        productMapper.updateById(product);
        // 上下架同样要双删缓存，否则详情页会一直展示旧状态
        evictProductDetailCache(productId);
        return Result.success(status == 1 ? "商品已上架！" : "商品已下架！");
    }

    @Override
    public Result<String> deleteProduct(Long userId, Long productId) {
        Shop myShop = getMyShop(userId);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        Product product = productMapper.selectById(productId);
        if (product == null || !product.getShopId().equals(myShop.getId())) {
            return Result.error(403, "商品不存在或无权操作！");
        }
        // 🆕 级联删除 SKU
        skuService.removeByProductId(productId);
        productMapper.deleteById(productId);
        // 删除后清缓存，避免详情页继续从缓存读出“幽灵商品”
        evictProductDetailCache(productId);
        return Result.success("商品已删除！");
    }

    @Override
    public Result<Shop> getShopInfo(Long userId) {
        Shop shop = getMyShop(userId);
        if (shop == null) {
            return Result.error(404, "您还没有开通店铺！");
        }
        return Result.success(shop);
    }

    @Override
    public Result<String> updateShopInfo(Long userId, ShopApplyDto dto) {
        Shop shop = getMyShop(userId);
        if (shop == null) {
            return Result.error(404, "您还没有开通店铺！");
        }
        if (dto.getName() != null) shop.setName(dto.getName());
        if (dto.getDescription() != null) shop.setDescription(dto.getDescription());
        shopMapper.updateById(shop);
        return Result.success("店铺信息更新成功！");
    }

    @Override
    public Result<Map<String, Object>> getDashboard(Long userId) {
        Shop shop = getMyShop(userId);
        if (shop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        Long shopId = shop.getId();
        Map<String, Object> data = new HashMap<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // ====== 基础 7 卡片数据 ======

        // totalOrders — 所有订单
        QueryWrapper<TradeOrder> allQw = new QueryWrapper<>();
        allQw.eq("shop_id", shopId);
        data.put("totalOrders", traderOrderMapper.selectCount(allQw));

        // todayOrders — 今日订单
        QueryWrapper<TradeOrder> todayQw = new QueryWrapper<>();
        todayQw.eq("shop_id", shopId).ge("create_time", todayStart);
        data.put("todayOrders", traderOrderMapper.selectCount(todayQw));

        // todaySales — 今日销售额
        List<TradeOrder> todayPaidOrders = getPaidOrders(shopId, todayStart);
        BigDecimal todaySales = todayPaidOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("todaySales", todaySales);

        // totalSales — 总销售额
        List<TradeOrder> allPaidOrders = getPaidOrders(shopId, null);
        BigDecimal totalSales = allPaidOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("totalSales", totalSales);

        // pendingShipment — 待发货（status=1 已支付）
        QueryWrapper<TradeOrder> pendingQw = new QueryWrapper<>();
        pendingQw.eq("shop_id", shopId).eq("status", 1);
        data.put("pendingDelivery", traderOrderMapper.selectCount(pendingQw));
        data.put("pendingShipment", traderOrderMapper.selectCount(pendingQw)); // 前端别名

        // totalProducts — 商品总数
        QueryWrapper<Product> productQw = new QueryWrapper<>();
        productQw.eq("shop_id", shopId);
        data.put("totalProducts", productMapper.selectCount(productQw));

        // pendingRefunds — 待处理退款
        QueryWrapper<RefundOrder> refundQw = new QueryWrapper<>();
        refundQw.eq("shop_id", shopId).eq("status", 0);
        data.put("pendingRefunds", refundOrderMapper.selectCount(refundQw));

        // shopName, shopStatus
        data.put("shopName", shop.getName());
        data.put("shopStatus", shop.getStatus());

        // ====== 客户画像分布 ======
        data.put("customerTiers", computeCustomerTierDistribution(shopId));

        // ====== 商品转化 Top 5 ======
        data.put("productConversion", computeProductConversion(shopId));

        // ====== 优惠券 ROI ======
        data.put("couponRoi", computeCouponRoi(shopId));

        return Result.success(data);
    }

    // ==================== Dashboard 辅助方法 ====================

    /**
     * 获取有效订单（status IN 1,3,4,5）
     */
    private List<TradeOrder> getPaidOrders(Long shopId, LocalDateTime fromTime) {
        QueryWrapper<TradeOrder> qw = new QueryWrapper<>();
        qw.eq("shop_id", shopId).in("status", Arrays.asList(1, 3, 4, 5));
        if (fromTime != null) {
            qw.ge("create_time", fromTime);
        }
        return traderOrderMapper.selectList(qw);
    }

    /**
     * 客户画像分布：统计在该店购买过的用户的层级分布
     */
    private Map<String, Long> computeCustomerTierDistribution(Long shopId) {
        Map<String, Long> tiers = new LinkedHashMap<>();
        tiers.put("PREMIUM", 0L);
        tiers.put("MID", 0L);
        tiers.put("BUDGET", 0L);
        tiers.put("NEW", 0L);

        try {
            // 去重获取所有购买该店商品的 userId
            QueryWrapper<TradeOrder> qw = new QueryWrapper<>();
            qw.select("DISTINCT user_id").eq("shop_id", shopId)
                    .in("status", Arrays.asList(1, 3, 4, 5));
            qw.last("LIMIT 500");
            List<TradeOrder> orders = traderOrderMapper.selectList(qw);
            List<Long> userIds = orders.stream()
                    .map(TradeOrder::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (!userIds.isEmpty()) {
                QueryWrapper<UserProfile> profileQw = new QueryWrapper<>();
                profileQw.in("user_id", userIds);
                List<UserProfile> profiles = userProfileMapper.selectList(profileQw);

                for (UserProfile p : profiles) {
                    String tier = p.getUserTier() != null ? p.getUserTier() : "NEW";
                    tiers.merge(tier, 1L, Long::sum);
                }
            }
        } catch (Exception e) {
            log.warn("客户画像分布计算失败: shopId={}", shopId, e);
        }
        return tiers;
    }

    /**
     * 商品转化率 Top 5：浏览数（Redis 近似值） vs 下单数
     */
    private List<Map<String, Object>> computeProductConversion(Long shopId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            QueryWrapper<Product> qw = new QueryWrapper<>();
            qw.eq("shop_id", shopId).eq("status", 1);
            List<Product> products = productMapper.selectList(qw);

            for (Product p : products) {
                // 浏览数：Redis ZSet（近似，15分钟窗口）
                Long views = stringRedisTemplate.opsForZSet().zCard("product:viewers:" + p.getId());
                if (views == null) views = 0L;

                // 下单数
                QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
                itemQw.eq("product_id", p.getId());
                long orders = tradeOrderItemMapper.selectCount(itemQw);

                Map<String, Object> item = new HashMap<>();
                item.put("productId", p.getId());
                item.put("productName", p.getName());
                item.put("views", views);
                item.put("orders", orders);
                item.put("conversionRate", views > 0
                        ? String.format("%.1f%%", orders * 100.0 / views)
                        : "N/A");
                result.add(item);
            }

            // 按下单数降序取 Top 5
            result.sort((a, b) -> Long.compare(
                    (Long) b.getOrDefault("orders", 0L),
                    (Long) a.getOrDefault("orders", 0L)));
            result = result.subList(0, Math.min(5, result.size()));
        } catch (Exception e) {
            log.warn("商品转化率计算失败: shopId={}", shopId, e);
        }
        return result;
    }

    /**
     * 优惠券 ROI：发放数 / 领取数 / 使用数 / 带来的 GMV
     */
    private Map<String, Object> computeCouponRoi(Long shopId) {
        Map<String, Object> roi = new HashMap<>();
        try {
            QueryWrapper<Coupon> qw = new QueryWrapper<>();
            qw.eq("shop_id", shopId);
            List<Coupon> coupons = couponMapper.selectList(qw);

            long totalIssued = 0;
            long totalClaimed = 0;
            long totalUsed = 0;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            BigDecimal totalRevenue = BigDecimal.ZERO;

            for (Coupon c : coupons) {
                totalIssued += c.getTotalCount() != null ? c.getTotalCount() : 0;
                int remain = c.getRemainCount() != null ? c.getRemainCount() : 0;
                totalClaimed += Math.max(0, (c.getTotalCount() != null ? c.getTotalCount() : 0) - remain);

                // 已使用数
                QueryWrapper<UserCoupon> usedQw = new QueryWrapper<>();
                usedQw.eq("coupon_id", c.getId()).eq("status", 1);
                totalUsed += userCouponMapper.selectCount(usedQw);

                // GMV：使用了该券的订单
                QueryWrapper<TradeOrder> orderQw = new QueryWrapper<>();
                orderQw.eq("coupon_id", c.getId()).in("status", Arrays.asList(1, 3, 4, 5));
                List<TradeOrder> couponOrders = traderOrderMapper.selectList(orderQw);
                for (TradeOrder o : couponOrders) {
                    totalDiscount = totalDiscount.add(
                            o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO);
                    totalRevenue = totalRevenue.add(
                            o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
                }
            }

            roi.put("totalIssued", totalIssued);
            roi.put("totalClaimed", totalClaimed);
            roi.put("totalUsed", totalUsed);
            roi.put("totalDiscountAmount", totalDiscount);
            roi.put("totalRevenue", totalRevenue);

            // ROI = (revenue - discount) / discount * 100%
            if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal net = totalRevenue.subtract(totalDiscount);
                roi.put("roiPercent", String.format("%.1f%%",
                        net.divide(totalDiscount, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))));
            } else {
                roi.put("roiPercent", "N/A");
            }
        } catch (Exception e) {
            log.warn("优惠券 ROI 计算失败: shopId={}", shopId, e);
            roi.put("totalIssued", 0);
            roi.put("totalClaimed", 0);
            roi.put("totalUsed", 0);
            roi.put("roiPercent", "N/A");
        }
        return roi;
    }

    private Shop getMyShop(Long userId) {
        QueryWrapper<Shop> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        return shopMapper.selectOne(qw);
    }
}
