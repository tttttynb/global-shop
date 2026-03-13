package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CartAddDto;
import com.bohao.globalshop.entity.CartItem;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.CartItemMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.service.CartService;
import com.bohao.globalshop.vo.CartItemVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private ProductMapper productMapper;

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
    public Result<List<CartItemVo>> getMyCart(Long userId) {
        // 1. 先查出这个用户在数据库里最原始的购物车记录
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).orderByDesc("create_time");
        List<CartItem> cartItems = cartItemMapper.selectList(queryWrapper);
        // 2. 准备一个空的 VO 集合，用来装马上要拼装好的豪华版数据
        List<CartItemVo> voList = new ArrayList<>();
        // 3. 遍历原始记录，去商品表里捞详情，然后“合体”
        for (CartItem item : cartItems) {
            Product product = productMapper.selectById(item.getProductId());
            // 如果商品还没被老板彻底从数据库删掉，我们就把它展示出来
            if (product != null) {
                CartItemVo vo = new CartItemVo();
                // 塞入购物车基础信息
                vo.setCartItemId(item.getId());
                vo.setProductId(item.getProductId());
                vo.setQuantity(item.getQuantity());
                // 塞入商品豪华信息
                vo.setProductName(product.getName());
                vo.setCoverImage(product.getCoverImage());
                vo.setPrice(product.getPrice());
                // 计算小计金额 (单价 × 数量)
                vo.setItemTotalAmount(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
                // 把拼装好的完整数据，放进大集合里
                voList.add(vo);
            }
        }
        return Result.success(voList);
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
}