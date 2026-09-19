package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Coupon;
import com.bohao.globalshop.entity.PointsRecord;
import com.bohao.globalshop.service.PointsService;
import com.bohao.globalshop.vo.PointsSummaryVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会员积分中心（Phase 4 - F8）
 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    /** 积分概览：可用积分 / 等级 / 成长值进度 / 签到状态 */
    @GetMapping("/summary")
    public Result<PointsSummaryVo> getSummary(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(pointsService.getSummary(userId));
    }

    /** 积分流水 */
    @GetMapping("/records")
    public Result<List<PointsRecord>> getRecords(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(pointsService.getRecords(userId));
    }

    /** 每日签到 */
    @PostMapping("/sign-in")
    public Result<String> signIn(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return pointsService.signIn(userId);
    }

    /** 完善画像领积分（联动 UserProfile 冷启动） */
    @PostMapping("/claim-profile-bonus")
    public Result<String> claimProfileBonus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return pointsService.claimProfileBonus(userId);
    }

    /** 积分商城：可兑换优惠券列表 */
    @GetMapping("/exchange/coupons")
    public Result<List<Coupon>> getExchangeableCoupons() {
        return Result.success(pointsService.getExchangeableCoupons());
    }

    /** 积分兑换优惠券 */
    @PostMapping("/exchange/{couponId}")
    public Result<String> exchangeCoupon(HttpServletRequest request, @PathVariable("couponId") Long couponId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return pointsService.exchangeCoupon(userId, couponId);
    }

    /** 下单抵扣试算：{pointsUsed, deductAmount} */
    @GetMapping("/deduction-preview")
    public Result<Map<String, Object>> previewDeduction(HttpServletRequest request,
                                                        @RequestParam("amount") BigDecimal amount) {
        Long userId = (Long) request.getAttribute("currentUserId");
        PointsService.PointsDeduction d = pointsService.previewDeduction(userId, amount);
        return Result.success(Map.of(
                "pointsUsed", d.pointsUsed(),
                "deductAmount", d.deductionAmount()));
    }
}
