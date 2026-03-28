package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.dto.ReviewSubmitDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.vo.OrderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CartItemMapper cartItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShopMapper shopMapper;
    private final ProductReviewMapper productReviewMapper;
    private final DefaultRedisScript<Long> seckillScript;

    @Override
    @Transactional// 开启数据库事务，保证扣库存和下订单同生共死！！！
    public Result<String> createOrder(Long userId, OrderCreateDto dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            return Result.error(400, "商品不存在！");
        }
        if (product.getStock() < dto.getQuantity()) {
            return Result.error(400, "抱歉，商品库存不足！");
        }
        product.setStock(product.getStock() - dto.getQuantity());
        int updateResult = productMapper.updateById(product);
        if (updateResult == 0) {
            // 如果返回 0，说明在这个短短的几毫秒内，有别人抢先更新了这条数据，版本号对不上了！
            // 此时直接拦截，后面的生成订单代码根本就不会执行！
            return Result.error(500, "哎呀，活动太火爆了，商品被别人抢先一步啦！请重试。");
        }
        //计算总价 (单价 × 数量，BigDecimal 的乘法必须用 multiply 方法)
        BigDecimal totalAmount = product.getPrice().multiply(new BigDecimal(dto.getQuantity()));

        // 1. 创建主订单
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        traderOrderMapper.insert(order);

        //新增：把订单号塞进 Redis 延迟队列！
        Long expireTime = System.currentTimeMillis() + (15 * 60 * 1000);
        stringRedisTemplate.opsForZSet().add("order:timeout:queue", String.valueOf(order.getId()), expireTime);

        // 2. 创建订单详情
        TradeOrderItem orderItem = new TradeOrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setCoverImage(product.getCoverImage());
        orderItem.setPrice(product.getPrice());
        orderItem.setQuantity(dto.getQuantity());
        orderItem.setTotalAmount(totalAmount);
        tradeOrderItemMapper.insert(orderItem);

        return Result.success("太棒了！订单创建成功，总共消费：" + totalAmount + " 元！");
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
            vo.setCreateTime(order.getCreateTime());
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
        // 1.查出这笔订单
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.error(400, "哎呀，没找到这笔订单！");
        }
        // 2.安全校验一：防越权（不能替别人付钱，也不能用别人的钱付自己的订单）
        if (!order.getUserId().equals(userId)) {
            return Result.error(403, "警告：非法请求！这并非您的订单！");
        }
        // 3.安全校验二：防重复支付（只有状态是 0 待支付的订单才能付钱）
        if (order.getStatus() != 0) {
            return Result.error(400, "这笔订单已经支付过啦，请勿重复付款！");
        }
        // 4. 查出用户的钱包余额
        User user = userMapper.selectById(userId);
        // 5.核心逻辑：判断余额够不够？ (compareTo 返回 -1 代表小于)
        if (user.getBalance().compareTo(order.getTotalAmount()) < 0) {
            return Result.error(400, "老板，您的余额不足啦，请先充值！");
        }
        // 6.开始扣钱！(当前余额 - 订单总额)
        user.setBalance(user.getBalance().subtract(order.getTotalAmount()));
        userMapper.updateById(user); // 更新回数据库
        // 7.修改订单状态为 1 (已支付)
        order.setStatus(1);
        traderOrderMapper.updateById(order);
        return Result.success("支付成功！扣款：" + order.getTotalAmount() + " 元。老板大气！");
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

                // ==========================================
                // 🚀 大厂秒杀核心：Redis + Lua 分布式原子预扣减！
                // ==========================================
                // 构造这个商品在 Redis 里的库存 Key (例如: seckill:stock:1)
                String stockKey = "seckill:stock:" + product.getId();

                // 向 Redis 发射 Lua 脚本！
                // 参数1: 脚本本身; 参数2: KEYS数组(只传一个Key); 参数3: ARGV数组(购买数量)
                Long luaResult = stringRedisTemplate.execute(
                        seckillScript,
                        Collections.singletonList(stockKey),
                        String.valueOf(item.getQuantity())
                );

                // 🚨 如果 Lua 脚本返回 0，说明 Redis 里的库存已经被抢光了，直接无情拒绝！
                if (luaResult == null || luaResult == 0L) {
                    throw new RuntimeException("💥 哎呀手慢了！商品 [" + product.getName() + "] 已被抢空！");
                }

                // 👇 只有成功通过了 Redis Lua 脚本的“幸运儿”，才有资格继续往下走！
                // 在真实的秒杀系统里，走到这里通常会把订单丢进 RabbitMQ 异步写库。
                // 咱们这里为了保持闭环，既然 Redis 已经扣减成功，我们直接把通过了拦截的合法请求同步写进 MySQL：
                product.setStock(product.getStock() - item.getQuantity());
                productMapper.updateById(product);
                //计算这件商品的小计
                BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(item.getQuantity()));
                shopTotalAmount = shopTotalAmount.add(itemAmount);
                //准备订单项
                TradeOrderItem orderItem = new TradeOrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setCoverImage(product.getCoverImage());
                orderItem.setPrice(product.getPrice());
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
            // 5. 将刚才暂存的【子订单明细】绑上主订单 ID，并存入数据库
            for (TradeOrderItem orderItem : orderItems) {
                orderItem.setOrderId(order.getId());
                tradeOrderItemMapper.insert(orderItem);
            }
            // 6. 联动高级架构：把刚生成的这个新订单，推入 Redis 延迟队列！
            // 假设 15 分钟不付钱就取消（这里用 15 * 60 * 1000 毫秒）
            long expireTime = System.currentTimeMillis() + 15 * 60 * 1000;
            stringRedisTemplate.opsForZSet().add("order:timeout:queue", String.valueOf(order.getId()), expireTime);
            System.out.println("✅ 拆单成功：为店铺 [" + shopId + "] 生成了订单 [" + order.getId() + "]，金额:" + shopTotalAmount);
        }
        return Result.success("🎉 购物车合并结算成功！系统已自动为您拆分为 " + shopCartMap.size() + " 笔独立订单，请前往支付！");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelSingleOrder(Long orderId) {
        TradeOrder order = traderOrderMapper.selectById(orderId);
        // 如果订单存在，并且依然是 0 (待支付) 状态
        if (order.getStatus() == 0 || order != null) {
            // 1. 改为已取消
            order.setStatus(2);
            traderOrderMapper.updateById(order);
        }
        // 2. 查出子订单明细，把库存加回去
        QueryWrapper<TradeOrderItem> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        List<TradeOrderItem> items = tradeOrderItemMapper.selectList(wrapper);
        for (TradeOrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (item != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productMapper.updateById(product);
            }
        }
        return Result.success("订单取消成功！");
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
            User merchant = userMapper.selectById(userId);
            if (merchant != null) {
                //2.3 开始打钱！商家的当前余额 + 这笔订单的总金额
                merchant.setBalance(merchant.getBalance().add(order.getTotalAmount()));
                userMapper.updateById(merchant);
                System.out.println("财务播报：已成功向商家 [" + merchant.getId() + "] 的钱包转入货款：" + order.getTotalAmount() + "元！");
            }
        }
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

        return Result.success("🎉 感谢您的五星好评！评价发布成功！");
    }
}
