package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CouponCreateDto;
import com.bohao.globalshop.entity.Coupon;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.UserCoupon;
import com.bohao.globalshop.mapper.CouponMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.mapper.UserCouponMapper;
import com.bohao.globalshop.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ShopMapper shopMapper;

    @Override
    public Result<String> createCoupon(Long userId, CouponCreateDto dto) {
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop shop = shopMapper.selectOne(shopQw);
        if (shop == null) {
            return Result.error(403, "您还没有店铺！");
        }

        Coupon coupon = new Coupon();
        coupon.setShopId(shop.getId());
        coupon.setName(dto.getName());
        coupon.setType(dto.getType());
        coupon.setDiscountValue(dto.getDiscountValue());
        coupon.setMinAmount(dto.getMinAmount());
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        coupon.setTotalCount(dto.getTotalCount());
        coupon.setRemainCount(dto.getTotalCount());
        coupon.setStatus(1);
        coupon.setCreateTime(LocalDateTime.now());
        couponMapper.insert(coupon);
        return Result.success("优惠券创建成功！");
    }

    @Override
    public Result<List<Coupon>> getMerchantCoupons(Long userId) {
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop shop = shopMapper.selectOne(shopQw);
        if (shop == null) {
            return Result.success(Collections.emptyList());
        }
        QueryWrapper<Coupon> qw = new QueryWrapper<>();
        qw.eq("shop_id", shop.getId()).orderByDesc("create_time");
        return Result.success(couponMapper.selectList(qw));
    }

    @Override
    @Transactional
    public Result<String> claimCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return Result.error(404, "优惠券不存在！");
        }
        if (coupon.getRemainCount() <= 0) {
            return Result.error(400, "优惠券已领完！");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            return Result.error(400, "优惠券不在有效期内！");
        }
        // 检查是否已领取
        QueryWrapper<UserCoupon> checkQw = new QueryWrapper<>();
        checkQw.eq("user_id", userId).eq("coupon_id", couponId);
        if (userCouponMapper.selectCount(checkQw) > 0) {
            return Result.error(400, "您已领取过该优惠券！");
        }
        // 扣减库存
        coupon.setRemainCount(coupon.getRemainCount() - 1);
        couponMapper.updateById(coupon);
        // 创建用户优惠券记录
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0);
        userCoupon.setCreateTime(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);
        return Result.success("领取成功！");
    }

    @Override
    public Result<List<Map<String, Object>>> getMyCoupons(Long userId) {
        QueryWrapper<UserCoupon> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("create_time");
        List<UserCoupon> userCoupons = userCouponMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserCoupon uc : userCoupons) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("userCouponId", uc.getId());
                item.put("couponId", coupon.getId());
                item.put("name", coupon.getName());
                item.put("type", coupon.getType());
                item.put("discountValue", coupon.getDiscountValue());
                item.put("minAmount", coupon.getMinAmount());
                item.put("endTime", coupon.getEndTime());
                item.put("status", uc.getStatus());
                item.put("shopId", coupon.getShopId());
                result.add(item);
            }
        }
        return Result.success(result);
    }

    @Override
    public Result<List<Map<String, Object>>> getAvailableCoupons(Long userId, Long shopId, BigDecimal amount) {
        QueryWrapper<UserCoupon> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("status", 0);
        List<UserCoupon> userCoupons = userCouponMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserCoupon uc : userCoupons) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null) continue;
            // 检查店铺匹配
            if (shopId != null && !coupon.getShopId().equals(shopId)) continue;
            // 检查有效期
            if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) continue;
            // 检查满减门槛
            if (amount != null && coupon.getMinAmount() != null && amount.compareTo(coupon.getMinAmount()) < 0) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("userCouponId", uc.getId());
            item.put("couponId", coupon.getId());
            item.put("name", coupon.getName());
            item.put("type", coupon.getType());
            item.put("discountValue", coupon.getDiscountValue());
            item.put("minAmount", coupon.getMinAmount());
            item.put("endTime", coupon.getEndTime());
            result.add(item);
        }
        return Result.success(result);
    }
}
