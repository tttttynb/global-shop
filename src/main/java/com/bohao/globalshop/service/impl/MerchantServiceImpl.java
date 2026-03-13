package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {
    public final ShopMapper shopMapper;
    public final ProductMapper productMapper;

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
}
