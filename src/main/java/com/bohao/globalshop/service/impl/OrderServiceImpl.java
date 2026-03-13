package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.entity.CartItem;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.entity.TradeOrderItem;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.vo.OrderVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CartItemMapper cartItemMapper;
    private final StringRedisTemplate stringRedisTemplate;

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

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<TradeOrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            // 校验一：商品还在不在？
            if (product == null || product.getStatus() == 0) {
                throw new RuntimeException("商品 [" + cartItem.getProductId() + "] 不存在或已下架，结算失败！");
            }
            // 校验二：库存够不够？
            if (product.getStock() < cartItem.getQuantity()) {
                throw new RuntimeException("商品 [" + product.getName() + "] 库存不足，结算失败！");
            }
            // 3. 扣减库存 (触发乐观锁机制)
            product.setStock(product.getStock() - cartItem.getQuantity());
            int updateResult = productMapper.updateById(product);
            if (updateResult == 0) {
                // 如果返回 0，说明发生高并发冲突，直接抛出异常，让整个结算大回滚！
                throw new RuntimeException("哎呀，商品 [" + product.getName() + "] 被别人抢先一步啦，请重新结算！");
            }
            // 计算该项小计
            BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemAmount);
            // 准备订单项
            TradeOrderItem item = new TradeOrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setCoverImage(product.getCoverImage());
            item.setPrice(product.getPrice());
            item.setQuantity(cartItem.getQuantity());
            item.setTotalAmount(itemAmount);
            orderItems.add(item);
        }
        // 4. 创建主订单
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0); // 状态：0-待支付
        traderOrderMapper.insert(order);

        //新增：把订单号塞进 Redis 延迟队列！
        Long expireTime = System.currentTimeMillis() + (15 * 60 * 1000);
        stringRedisTemplate.opsForZSet().add("order:timeout:queue", String.valueOf(order.getId()), expireTime);

        // 5. 保存订单项
        for (TradeOrderItem item : orderItems) {
            item.setOrderId(order.getId());
            tradeOrderItemMapper.insert(item);
        }
        // 6. 清空购物车 (批量删除)
        cartItemMapper.delete(queryWrapper);
        return Result.success("购物车结算成功！请前往订单列表支付。");
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
}
