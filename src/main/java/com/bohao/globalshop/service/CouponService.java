package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.CouponCreateDto;
import com.bohao.globalshop.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CouponService {
    Result<String> createCoupon(Long userId, CouponCreateDto dto);

    Result<List<Coupon>> getMerchantCoupons(Long userId);

    Result<String> claimCoupon(Long userId, Long couponId);

    Result<List<Map<String, Object>>> getMyCoupons(Long userId);

    Result<List<Map<String, Object>>> getAvailableCoupons(Long userId, Long shopId, BigDecimal amount);
}
