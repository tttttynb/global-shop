package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.entity.TradeOrderItem;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.mapper.TradeOrderItemMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.MerchantService;
import com.bohao.globalshop.vo.OrderVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {
    public final ShopMapper shopMapper;
    public final ProductMapper productMapper;
    public final TraderOrderMapper traderOrderMapper;
    public final TradeOrderItemMapper tradeOrderItemMapper;


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
        product.setName(myShop.getName());
        product.setDescription(myShop.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCoverImage(dto.getCoverImage());
        product.setStatus(1);
        productMapper.insert(product);
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
            vo.setCreateTime(order.getCreateTime());
            // 查出这个订单买了啥商品
            QueryWrapper<TradeOrderItem> itemQw = new QueryWrapper<>();
            itemQw.eq("order_id", order.getId());
            vo.setItems(tradeOrderItemMapper.selectList(itemQw));
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<String> deliverOrder(Long userId, Long orderId) {
        // 1. 查出当前老板的店铺
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop myShop = shopMapper.selectOne(shopQw);
        if (myShop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        //2.查出这批订单
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.error(404, "找不到该订单！");
        }
        // 3.核心风控：越权校验！这个订单是你的吗？
        if (!order.getShopId().equals(myShop.getId())) {
            return Result.error(403, "严重警告：越权操作！您不能发别人店铺的货！");
        }
        // 4. 状态校验：买家付钱了吗？
        if (order.getStatus() == 0) {
            return Result.error(400, "买家还没付钱呢，不能发货哦！");
        } else if (order.getStatus() == 2) {
            return Result.error(400, "订单已超时取消，无法发货！");
        } else if (order.getStatus() == 3) {
            return Result.error(400, "该订单已经发过货啦！");
        }
        // 5. 修改状态为 3 (已发货)
        order.setStatus(3);
        traderOrderMapper.updateById(order);

        return Result.success("🚀 发货成功！商品正在马不停蹄地奔向买家！");
    }
}
