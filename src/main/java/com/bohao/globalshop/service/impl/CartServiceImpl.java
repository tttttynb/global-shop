package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.entity.CartItem;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.mapper.CartItemMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.service.CartService;
import com.bohao.globalshop.vo.CartItemVo;
import com.bohao.globalshop.vo.CartShopVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;

    private final ProductMapper productMapper;

    private final ShopMapper shopMapper;

    @Override
    public Result<String> addToCart(Long userId, CartAddDto dto) {
        // 1. 检查商品是否存在，或者是否下架
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null || product.getStatus() == 0) {
            return Result.error(400, "哎呀，商品不存在或已下架！");
        }
        // 2. 查一下这个用户的购物车里，是不是已经有这件商品了？
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("product_id", dto.getProductId());
        CartItem existItem = cartItemMapper.selectOne(queryWrapper);
        if (existItem != null) {
            // 3. 如果已经有了，就做“合并同类项”（老数量 + 新数量）
            existItem.setQuantity(existItem.getQuantity() + dto.getQuantity());
            cartItemMapper.updateById(existItem);
            return Result.success("购物车商品数量已更新！");
        } else {
            // 4. 如果没有，就创建一条全新的购物车记录
            CartItem newItem = new CartItem();
            newItem.setUserId(userId);
            newItem.setProductId(dto.getProductId());
            newItem.setQuantity(dto.getQuantity());
            cartItemMapper.insert(newItem);
            return Result.success("成功加入购物车！");
        }
    }

    @Override
    public Result<List<CartShopVo>> getCartList(Long userId) {
        // 1. 查出当前用户所有的购物车记录
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        List<CartItem> cartItems = cartItemMapper.selectList(queryWrapper);
        // 2. 准备一个 Map 用来按 shopId 分组聚合数据
        // 用 LinkedHashMap 是为了保持放入的顺序
        LinkedHashMap<Long, CartShopVo> shopMap = new LinkedHashMap<>();
        // 3. 开始遍历拼接
        for (CartItem item : cartItems) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null)
                continue;
            Long shopId = product.getShopId();
            if (shopId == null) {
                continue;
            }
            //4.看看这个店铺的VO是不是已经在Map里的？如果没有，新建一个
            CartShopVo shopVo = shopMap.computeIfAbsent(shopId, key -> {
                CartShopVo cartShopVo = new CartShopVo();
                cartShopVo.setShopId(shopId);
                //查出真实店铺名
                Shop shop = shopMapper.selectById(shopId);
                cartShopVo.setShopName(shop != null ? shop.getName() : "已注销店铺");
                cartShopVo.setItems(new ArrayList<>());
                return cartShopVo;
            });
            //5.组装底层的商品明细
            CartItemVo itemVo = new CartItemVo();
            itemVo.setCartItemId(item.getId());
            itemVo.setProductId(product.getId());
            itemVo.setProductName(product.getName());
            itemVo.setCoverImage(product.getCoverImage());
            itemVo.setPrice(product.getPrice());
            itemVo.setQuantity(item.getQuantity());
            itemVo.setItemTotalAmount(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
            //6.把商品塞进对应的VO里
            shopVo.getItems().add(itemVo);
        }
        // 7. 把 Map 里的 values 提取出来变成 List 返回给前端
        return Result.success(new ArrayList<>(shopMap.values()));
    }

    @Override
    public Result<String> removeCartItem(Long userId, Long cartItemId) {
        // 1. 先去数据库里把这条购物车记录查出来
        CartItem item = cartItemMapper.selectById(cartItemId);
        //2.判空放错
        if (item == null) {
            return Result.error(400, "哎呀，购物车里没找到这件商品！");
        }
        // 3. 🚨 核心安全校验：判断这个购物车记录的 userId，是不是当前登录的 userId？
        // 绝对不能让张三删掉李四的购物车！
        if (!item.getUserId().equals(userId)) {
            return Result.error(403, "警告：越权操作！你不能删除别人的购物车！");
        }
        // 4. 校验通过，安全删除！
        cartItemMapper.deleteById(cartItemId);
        return Result.success("商品已成功移出购物车！");
    }

    @Override
    public Result<String> updateQuantity(Long userId, Long cartItemId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            return Result.error(400, "商品数量不能小于1！");
        }
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null) {
            return Result.error(400, "购物车里没找到这件商品！");
        }
        if (!item.getUserId().equals(userId)) {
            return Result.error(403, "越权操作！你不能修改别人的购物车！");
        }
        // 检查库存
        Product product = productMapper.selectById(item.getProductId());
        if (product != null && quantity > product.getStock()) {
            return Result.error(400, "抱歉，商品库存仅剩 " + product.getStock() + " 件！");
        }
        item.setQuantity(quantity);
        cartItemMapper.updateById(item);
        return Result.success("购物车数量已更新！");
    }
}