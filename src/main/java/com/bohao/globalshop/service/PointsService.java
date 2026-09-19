package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Coupon;
import com.bohao.globalshop.entity.MemberPoints;
import com.bohao.globalshop.entity.PointsRecord;
import com.bohao.globalshop.vo.PointsSummaryVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员积分体系（Phase 4 - F8）
 * <p>
 * 获取：下单返积分 / 每日签到 / 评价晒单 / 完善画像；
 * 消耗：下单抵扣 / 兑换优惠券；
 * 等级：按累计消费升级（青铜→白银→黄金→钻石），享下单折扣，等级信息注入 AI 导购提示词。
 */
public interface PointsService {

    /** 积分抵扣结果（下单事务内使用） */
    record PointsDeduction(int pointsUsed, BigDecimal deductionAmount) {
        public static final PointsDeduction ZERO = new PointsDeduction(0, BigDecimal.ZERO);
    }

    MemberPoints getOrCreateAccount(Long userId);

    PointsSummaryVo getSummary(Long userId);

    /** 每日签到（Redis 防重 + 连签加成） */
    Result<String> signIn(Long userId);

    List<PointsRecord> getRecords(Long userId);

    /** 试算：本单最多可抵扣多少（不扣积分） */
    PointsDeduction previewDeduction(Long userId, BigDecimal goodsAmount);

    /** 下单积分抵扣（订单事务内调用，原子扣减） */
    PointsDeduction deductForOrder(Long userId, Long orderId, BigDecimal goodsAmount);

    /** 订单取消 → 返还抵扣积分 */
    void refundOrderDeduction(Long userId, Long orderId);

    /** 支付成功 → 下单返积分 + 成长值累计 + 等级晋升 */
    void onOrderPaid(Long userId, Long orderId, BigDecimal payAmount);

    /** 评价晒单 → 返积分（每日限次防刷） */
    void onReviewCreated(Long userId, Long reviewId);

    /** 完善画像领积分（一次性，联动 UserProfile 冷启动） */
    Result<String> claimProfileBonus(Long userId);

    /** 积分兑换优惠券 */
    Result<String> exchangeCoupon(Long userId, Long couponId);

    /** 积分商城：可兑换的优惠券列表 */
    List<Coupon> getExchangeableCoupons();

    /** 当前用户等级折扣率（0.02 = 下单立减2%），未启用/无账户返回 0 */
    BigDecimal getLevelDiscount(Long userId);

    /** 会员等级提示词片段（注入个性化 AI 导购 Agent），未启用返回空串 */
    String getMemberLevelPrompt(Long userId);
}
