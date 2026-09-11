package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.config.RabbitMqConfig;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.dto.PaymentCreateDto;
import com.bohao.globalshop.dto.ReviewSubmitDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.enums.PaymentChannel;
import com.bohao.globalshop.event.OrderCompletedEvent;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.PaymentService;
import com.bohao.globalshop.vo.OrderVo;
import com.bohao.globalshop.vo.PaymentResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CartItemMapper cartItemMapper;
    private final ShopMapper shopMapper;
    private final ProductReviewMapper productReviewMapper;
    private final RabbitTemplate rabbitTemplate;
    private final UserAddressMapper userAddressMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final com.bohao.globalshop.service.SkuService skuService;

    @Override
    @Transactional// 开启数据库事务，保证扣库存和下订单同生共死！！！
    public Result<String> createOrder(Long userId, OrderCreateDto dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            return Result.error(400, "商品不存在！");
        }
        // 🆕 SKU 化：解析目标规格（skuId 为空自动落到默认 SKU，兼容单规格商品）
        ProductSku sku = skuService.resolveSku(dto.getProductId(), dto.getSkuId());
        if (sku == null) {
            return Result.error(400, "该商品规格不存在或已停售！");
        }
        if (sku.getStock() < dto.getQuantity()) {
            return Result.error(400, "抱歉，规格 [" + sku.getSpecText() + "] 库存不足！");
        }
        // 🚀 第一层护城河：Redis + Lua 原子预扣减（SKU 维度，防高并发超卖）
        if (!skuService.deductRedisStock(sku.getId(), dto.getQuantity())) {
            return Result.error(500, "哎呀，活动太火爆了，该规格已被抢空！");
        }
        // 🚀 第二层护城河：MySQL 原子扣减 UPDATE ... SET stock = stock - N WHERE stock >= N
        if (!skuService.deductStock(sku.getId(), dto.getQuantity())) {
            // DB 扣减失败，把 Redis 预扣的库存还回去
            skuService.restoreRedisStock(sku.getId(), dto.getQuantity());
            return Result.error(500, "哎呀，活动太火爆了，商品被别人抢先一步啦！请重试。");
        }
        // 同步商品表冗余展示字段（price=最低SKU价, stock=SKU库存之和）
        skuService.syncProductAggregate(product.getId());
        //计算总价 (SKU 单价 × 数量)
        BigDecimal totalAmount = sku.getPrice().multiply(new BigDecimal(dto.getQuantity()));

        // 优惠券抵扣逻辑
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (dto.getCouponId() != null) {
            com.bohao.globalshop.entity.UserCoupon userCoupon = userCouponMapper.selectById(dto.getCouponId());
            if (userCoupon != null && userCoupon.getUserId().equals(userId) && userCoupon.getStatus() == 0) {
                com.bohao.globalshop.entity.Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
                if (coupon != null && totalAmount.compareTo(coupon.getMinAmount()) >= 0) {
                    if (coupon.getType() == 1) {
                        discountAmount = coupon.getDiscountValue();
                    } else if (coupon.getType() == 2) {
                        discountAmount = totalAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountValue().divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP)));
                    }
                    if (discountAmount.compareTo(totalAmount) > 0) {
                        discountAmount = totalAmount;
                    }
                    totalAmount = totalAmount.subtract(discountAmount);
                    // 标记优惠券已使用
                    userCoupon.setStatus(1);
                    userCoupon.setUseTime(java.time.LocalDateTime.now());
                    userCouponMapper.updateById(userCoupon);
                }
            }
        }

        // 1. 创建主订单
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setShopId(product.getShopId());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setCouponId(dto.getCouponId());
        order.setStatus(0);
        // 地址快照
        if (dto.getAddressId() != null) {
            UserAddress address = userAddressMapper.selectById(dto.getAddressId());
            if (address != null && address.getUserId().equals(userId)) {
                order.setReceiverName(address.getReceiverName());
                order.setReceiverPhone(address.getPhone());
                String fullAddress = (address.getProvince() != null ? address.getProvince() : "")
                        + (address.getCity() != null ? address.getCity() : "")
                        + (address.getDistrict() != null ? address.getDistrict() : "")
                        + address.getDetailAddress();
                order.setReceiverAddress(fullAddress);
            }
        }
        traderOrderMapper.insert(order);

        // 🚀 架构升级：把订单号作为消息，扔进 RabbitMQ 的延迟队列（等待区）！
        // ⚠️ 必须等事务提交后再发：事务内发送会让死信监听器读到未提交数据（查不到订单空跑）
        publishOrderTimeoutMessage(order.getId());

        // 2. 创建订单详情（🆕 带 SKU 快照：规格文本 + SKU 单价，历史订单可追溯）
        TradeOrderItem orderItem = new TradeOrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(product.getId());
        orderItem.setSkuId(sku.getId());
        orderItem.setSkuSpec(sku.getSpecText());
        orderItem.setProductName(product.getName());
        orderItem.setCoverImage(sku.getImage() != null && !sku.getImage().isEmpty() ? sku.getImage() : product.getCoverImage());
        orderItem.setPrice(sku.getPrice());
        orderItem.setQuantity(dto.getQuantity());
        orderItem.setTotalAmount(totalAmount);
        tradeOrderItemMapper.insert(orderItem);

        return Result.success(String.valueOf(order.getId()));
    }

    @Override
    public Result<List<OrderVo>> getMyOrders(Long userId) {
        //1.查出当前用户所有的主订单
        QueryWrapper<TradeOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        List<TradeOrder> parentOrders = traderOrderMapper.selectList(queryWrapper);
        //2.准备一个VO集合
        List<OrderVo> voList = new ArrayList<>();
        //3.遍历主订单，去子表里捞明细
        for (TradeOrder order : parentOrders) {
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
            //核心：根据主订单的ID，去trade_order_item表里查出属于它的所有商品！
            QueryWrapper<TradeOrderItem> itemWrapper = new QueryWrapper<>();
            itemWrapper.eq("order_id", order.getId());
            List<TradeOrderItem> items = tradeOrderItemMapper.selectList(itemWrapper);
            vo.setItems(items);
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    @Transactional
    public Result<String> payOrder(Long userId, Long orderId) {
        // 委托给支付网关（默认使用余额支付，保持向后兼容）
        PaymentCreateDto dto = new PaymentCreateDto();
        dto.setOrderId(orderId);
        dto.setChannel(PaymentChannel.BALANCE.getCode());
        Result<PaymentResultVo> result = paymentService.createPayment(userId, dto);
        if (result.getCode() == 200) {
            return Result.success("支付成功！扣款：" + result.getData().getAmount() + " 元。老板大气！");
        }
        return Result.error(result.getCode(), result.getMessage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)//任何一步出错，全部回滚！
    public Result<String> checkoutCart(Long userId) {
        // 1. 把这个用户购物车里的所有东西都捞出来
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        List<CartItem> cartItems = cartItemMapper.selectList(queryWrapper);
        if (cartItems == null || cartItems.isEmpty()) {
            return Result.error(400, "购物车空空如也，没东西可以结算哦！");
        }
        // 2. 核心算法：按店铺拆分购物车商品
        Map<Long, List<CartItem>> shopCartMap = new HashMap<>();
        for (CartItem item : cartItems) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null || product.getStatus() != 1) {
                throw new RuntimeException("商品 [" + item.getProductId() + "] 不存在或已下架，结算失败！");
            }
            // 塞进对应店铺的 List 里
            shopCartMap.computeIfAbsent(product.getShopId(), k -> new ArrayList<>()).add(item);
        }
        List<String> createdOrderIds = new ArrayList<>();
        // 3.开始“拆单”流程！遍历每一个店铺
        for (Map.Entry<Long, List<CartItem>> entry : shopCartMap.entrySet()) {
            Long shopId = entry.getKey();
            List<CartItem> shopItems = entry.getValue();
            BigDecimal shopTotalAmount = BigDecimal.ZERO;
            List<TradeOrderItem> orderItems = new ArrayList<>();
            // 3.1 遍历这个店铺下的商品：算钱 + 扣库存
            for (CartItem item : shopItems) {
                Product product = productMapper.selectById(item.getProductId());
                if (product == null || product.getStatus() != 1) {
                    throw new RuntimeException("商品 [" + item.getProductId() + "] 不存在或已下架！");
                }
                // 🆕 SKU 化：解析购物车项对应的规格（skuId 为空/已被商家替换 → 落到默认 SKU）
                ProductSku sku = skuService.resolveSku(item.getProductId(), item.getSkuId());
                if (sku == null) {
                    sku = skuService.getOrCreateDefaultSku(item.getProductId());
                }
                if (sku == null) {
                    throw new RuntimeException("商品 [" + product.getName() + "] 规格数据异常，结算失败！");
                }

                // ==========================================
                // 🚀 大厂秒杀核心：Redis + Lua 分布式原子预扣减（SKU 维度）！
                // Key: seckill:stock:sku:{skuId}
                // ==========================================
                if (!skuService.deductRedisStock(sku.getId(), item.getQuantity())) {
                    throw new RuntimeException("💥 哎呀手慢了！商品 [" + product.getName() + " " + sku.getSpecText() + "] 已被抢空！");
                }
                // MySQL 原子扣减兜底，失败则回补 Redis
                if (!skuService.deductStock(sku.getId(), item.getQuantity())) {
                    skuService.restoreRedisStock(sku.getId(), item.getQuantity());
                    throw new RuntimeException("💥 商品 [" + product.getName() + " " + sku.getSpecText() + "] 库存不足，结算失败！");
                }
                // 同步商品表冗余展示字段
                skuService.syncProductAggregate(product.getId());
                //计算这件商品的小计（SKU 单价）
                BigDecimal itemAmount = sku.getPrice().multiply(new BigDecimal(item.getQuantity()));
                shopTotalAmount = shopTotalAmount.add(itemAmount);
                //准备订单项（🆕 带 SKU 快照）
                TradeOrderItem orderItem = new TradeOrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setSkuId(sku.getId());
                orderItem.setSkuSpec(item.getSkuSpec() != null ? item.getSkuSpec() : sku.getSpecText());
                orderItem.setProductName(product.getName());
                orderItem.setCoverImage(sku.getImage() != null && !sku.getImage().isEmpty() ? sku.getImage() : product.getCoverImage());
                orderItem.setPrice(sku.getPrice());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setTotalAmount(itemAmount);
                orderItems.add(orderItem);
                // 3.2 顺手把这件商品从购物车表里物理删除！
                cartItemMapper.deleteById(item.getId());
            }
            //4.为这个店铺生成专属主订单
            TradeOrder order = new TradeOrder();
            order.setUserId(userId);
            order.setShopId(shopId);
            order.setTotalAmount(shopTotalAmount);
            order.setStatus(0);
            traderOrderMapper.insert(order);
            createdOrderIds.add(String.valueOf(order.getId()));
            // 5. 将刚才暂存的【子订单明细】绑上主订单 ID，并存入数据库
            for (TradeOrderItem orderItem : orderItems) {
                orderItem.setOrderId(order.getId());
                tradeOrderItemMapper.insert(orderItem);
            }
//            // 6. 联动高级架构：把刚生成的这个新订单，推入 Redis 延迟队列！
//            // 假设 15 分钟不付钱就取消（这里用 15 * 60 * 1000 毫秒）
//            long expireTime = System.currentTimeMillis() + 15 * 60 * 1000;
//            stringRedisTemplate.opsForZSet().add("order:timeout:queue", String.valueOf(order.getId()), expireTime);
            // 6. 🚀 大厂架构：把刚生成的拆单主订单号，推入 RabbitMQ 延迟轨道！（事务提交后发送）
            publishOrderTimeoutMessage(order.getId());
            System.out.println("✅ 拆单成功：为店铺 [" + shopId + "] 生成了订单 [" + order.getId() + "]，金额:" + shopTotalAmount);
        }
//        return Result.success("🎉 购物车合并结算成功！系统已自动为您拆分为 " + shopCartMap.size() + " 笔独立订单，请前往支付！");
        return Result.success(String.join(",", createdOrderIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelSingleOrder(Long orderId) {
        TradeOrder order = traderOrderMapper.selectById(orderId);
        // 如果订单存在，并且依然是 0 (待支付) 状态
        if (order != null && order.getStatus() == 0) {
            // 1. 改为已取消
            order.setStatus(2);
            traderOrderMapper.updateById(order);
            // 2. 查出子订单明细，把库存加回去（🆕 SKU 维度回补：DB + Redis 双层）
            // ⚠️ 幂等关键：只有真正执行了取消（0→2）才回补库存！
            // 否则重复投递/重复触发时库存会被反复回补，越补越多
            QueryWrapper<TradeOrderItem> wrapper = new QueryWrapper<>();
            wrapper.eq("order_id", orderId);
            List<TradeOrderItem> items = tradeOrderItemMapper.selectList(wrapper);
            for (TradeOrderItem item : items) {
                if (item.getSkuId() != null) {
                    // 新订单：回补 SKU 库存
                    skuService.restoreStock(item.getSkuId(), item.getQuantity());
                    skuService.restoreRedisStock(item.getSkuId(), item.getQuantity());
                    skuService.syncProductAggregate(item.getProductId());
                } else {
                    // 存量旧订单（无 SKU 快照）：回补商品表库存，保持兼容
                    Product product = productMapper.selectById(item.getProductId());
                    if (product != null) {
                        product.setStock(product.getStock() + item.getQuantity());
                        productMapper.updateById(product);
                    }
                }
            }
            return Result.success("订单取消成功！库存已回退！");
        }
        // 订单已支付/已取消：什么都不做，保证幂等
        return Result.success("订单无需取消！");
    }

    /**
     * 发送订单超时取消的延迟消息（⚠️ 事务提交后再发！）
     * 在事务内发送的话，消息可能先于 COMMIT 到达监听器：
     * 监听器查不到未提交的订单而空跑，超时取消就此丢失，订单永远挂着。
     */
    private void publishOrderTimeoutMessage(Long orderId) {
        Runnable send = () -> rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_DELAY_EXCHANGE, RabbitMqConfig.ORDER_DELAY_ROUTING_KEY, orderId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class) // 涉及状态流转，加上事务保护
    public Result<String> confirmReceipt(Long userId, Long orderId) {
        //1.查出这笔订单
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.error(404, "哎呀，没找到这笔订单！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.error(403, "严重警告：非法请求！您无权操作别人的订单！");
        }
        if (order.getStatus() != 3) {
            return Result.error(400, "这笔订单当前还未发货或已完结，无法确认收货哦！");
        }
        order.setStatus(4);
        traderOrderMapper.updateById(order);
        //2.担保资金给商家
        //2.1找到卖这个产品的商家
        Shop shop = shopMapper.selectById(order.getShopId());
        if (shop != null) {
            //2.2找到卖这个产品的老板
            User merchant = userMapper.selectById(shop.getUserId());
            if (merchant != null) {
                //2.3 开始打钱！商家的当前余额 + 这笔订单的总金额
                merchant.setBalance(merchant.getBalance().add(order.getTotalAmount()));
                userMapper.updateById(merchant);
                System.out.println("财务播报：已成功向商家 [" + merchant.getId() + "] 的钱包转入货款：" + order.getTotalAmount() + "元！");
            }
        }
        // 🚀 发布订单完成事件，触发用户画像更新
        eventPublisher.publishEvent(new OrderCompletedEvent(this, userId, orderId, order.getTotalAmount()));
        return Result.success("🎉 确认收货成功！交易完成，快去给商品写个评价吧！");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> submitReview(Long userId, ReviewSubmitDto dto) {
        TradeOrderItem orderItem = tradeOrderItemMapper.selectById(dto.getOrderItemId());
        if (orderItem == null) {
            return Result.error(404, "订单不存在！");
        }
        TradeOrder mainOrder = traderOrderMapper.selectById(orderItem.getOrderId());
        if (!mainOrder.getUserId().equals(userId)) {
            return Result.error(403, "严重警告：你不能评价别人买的商品！");
        }
        if (mainOrder.getStatus() != 4) {
            return Result.error(400, "必须确认收货后才能发表评价哦！");
        }
        //防刷单校验（你是不是已经评过一次了？）
        QueryWrapper<ProductReview> reviewQw = new QueryWrapper<>();
        reviewQw.eq("order_item_id", dto.getOrderItemId());
        if (productReviewMapper.selectCount(reviewQw) > 0) {
            return Result.error(400, "您已经评价过该商品了，不能重复评价！");
        }
        //生成评价记录
        ProductReview review = new ProductReview();
        review.setUserId(userId);
        review.setProductId(orderItem.getProductId());
        review.setOrderItemId(dto.getOrderItemId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        productReviewMapper.insert(review);
        mainOrder.setStatus(5);
        traderOrderMapper.updateById(mainOrder);

        return Result.success("🎉 感谢您的五星好评！评价发布成功！");
    }

    @Override
    public Result<OrderVo> getOrderDetail(Long userId, Long orderId) {
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.error(404, "订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.error(403, "无权查看此订单！");
        }
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
        QueryWrapper<TradeOrderItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("order_id", order.getId());
        itemWrapper.eq("order_id", order.getId());
        vo.setItems(tradeOrderItemMapper.selectList(itemWrapper));
        return Result.success(vo);
    }
}
