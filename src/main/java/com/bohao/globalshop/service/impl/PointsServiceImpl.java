package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Coupon;
import com.bohao.globalshop.entity.MemberPoints;
import com.bohao.globalshop.entity.PointsRecord;
import com.bohao.globalshop.entity.UserCoupon;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.enums.NotificationTargetType;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.CouponMapper;
import com.bohao.globalshop.mapper.MemberPointsMapper;
import com.bohao.globalshop.mapper.PointsRecordMapper;
import com.bohao.globalshop.mapper.UserCouponMapper;
import com.bohao.globalshop.service.PointsService;
import com.bohao.globalshop.service.UserProfileService;
import com.bohao.globalshop.vo.PointsSummaryVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会员积分体系实现（Phase 4 - F8）
 * <p>
 * 防刷设计：签到 Redis SETNX 日级防重 + 评价返积分 Redis 日计数器限次 +
 * 下单返积分/画像奖励按 points_record 幂等去重（type + related_id）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private final MemberPointsMapper memberPointsMapper;
    private final PointsRecordMapper pointsRecordMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final UserProfileService userProfileService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** 功能总开关（沿用 personalization.enabled 同款配置模式） */
    @Value("${app.points.enabled:true}")
    private boolean enabled;
    /** 每消费 1 元返多少积分 */
    @Value("${app.points.earn-per-yuan:1}")
    private int earnPerYuan;
    /** 签到基础积分 */
    @Value("${app.points.sign-in-points:5}")
    private int signInPoints;
    /** 连续签到每天额外加成上限 */
    @Value("${app.points.sign-in-streak-bonus:5}")
    private int streakBonusCap;
    /** 评价晒单积分 */
    @Value("${app.points.review-points:10}")
    private int reviewPoints;
    /** 评价返积分每日限次（Redis 计数器防刷） */
    @Value("${app.points.review-daily-limit:3}")
    private int reviewDailyLimit;
    /** 完善画像一次性奖励 */
    @Value("${app.points.profile-bonus:50}")
    private int profileBonus;
    /** 多少积分抵 1 元 */
    @Value("${app.points.points-per-yuan:100}")
    private int pointsPerYuan;
    /** 单笔订单积分抵扣上限比例（商品金额） */
    @Value("${app.points.max-deduction-ratio:0.5}")
    private BigDecimal maxDeductionRatio;
    /** 等级成长值门槛 */
    @Value("${app.points.level-growth:0,1000,5000,20000}")
    private String levelGrowthConfig;
    /** 等级名称 */
    @Value("${app.points.level-names:青铜会员,白银会员,黄金会员,钻石会员}")
    private String levelNamesConfig;
    /** 等级折扣率（0.02 = 立减2%） */
    @Value("${app.points.level-discounts:0,0.01,0.02,0.03}")
    private String levelDiscountsConfig;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 账户 ====================

    @Override
    public MemberPoints getOrCreateAccount(Long userId) {
        MemberPoints account = memberPointsMapper.selectOne(new QueryWrapper<MemberPoints>()
                .eq("user_id", userId).last("LIMIT 1"));
        if (account != null) {
            return account;
        }
        account = new MemberPoints();
        account.setUserId(userId);
        account.setPoints(0);
        account.setTotalEarned(0);
        account.setGrowth(BigDecimal.ZERO);
        account.setLevel(1);
        account.setConsecutiveDays(0);
        try {
            memberPointsMapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 并发开户：uk_user 冲突时回读
            account = memberPointsMapper.selectOne(new QueryWrapper<MemberPoints>()
                    .eq("user_id", userId).last("LIMIT 1"));
        }
        return account;
    }

    @Override
    public PointsSummaryVo getSummary(Long userId) {
        MemberPoints account = getOrCreateAccount(userId);
        PointsSummaryVo vo = new PointsSummaryVo();
        vo.setPoints(account.getPoints());
        vo.setTotalEarned(account.getTotalEarned());
        vo.setGrowth(account.getGrowth());
        vo.setLevel(account.getLevel());
        vo.setLevelName(levelName(account.getLevel()));
        vo.setLevelDiscount(levelDiscount(account.getLevel()));
        BigDecimal[] thresholds = growthThresholds();
        if (account.getLevel() < thresholds.length) {
            vo.setNextLevelGap(thresholds[account.getLevel()].subtract(account.getGrowth()).max(BigDecimal.ZERO));
            vo.setNextLevelName(levelName(account.getLevel() + 1));
        } else {
            vo.setNextLevelGap(BigDecimal.ZERO);
            vo.setNextLevelName(null);
        }
        vo.setSignedToday(LocalDate.now().equals(account.getLastSignDate()));
        vo.setConsecutiveDays(account.getConsecutiveDays());
        vo.setPointsPerYuan(pointsPerYuan);
        return vo;
    }

    // ==================== 签到 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> signIn(Long userId) {
        if (!enabled) {
            return Result.error(400, "积分功能未开启");
        }
        // Redis SETNX 防连点（同一天只允许一次请求进入扣写逻辑）
        String dayKey = "points:sign:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(dayKey, "1", 2, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            return Result.error(400, "今天已经签到过啦，明天再来吧！");
        }
        MemberPoints account = getOrCreateAccount(userId);
        if (LocalDate.now().equals(account.getLastSignDate())) {
            return Result.error(400, "今天已经签到过啦，明天再来吧！");
        }
        // 连签判定：昨天签过 → 连签 +1，否则重置为 1
        int streak = LocalDate.now().minusDays(1).equals(account.getLastSignDate())
                ? account.getConsecutiveDays() + 1 : 1;
        int bonus = Math.min(Math.max(streak - 1, 0), streakBonusCap);
        int earned = signInPoints + bonus;

        account.setConsecutiveDays(streak);
        account.setLastSignDate(LocalDate.now());
        account.setPoints(account.getPoints() + earned);
        account.setTotalEarned(account.getTotalEarned() + earned);
        memberPointsMapper.updateById(account);

        insertRecord(userId, earned, PointsRecord.TYPE_SIGN_IN, null,
                "每日签到 +" + earned + (bonus > 0 ? "（连签" + streak + "天加成+" + bonus + "）" : ""));
        notifyUser(userId, "签到成功", "签到获得 " + earned + " 积分，已连续签到 " + streak + " 天！", null);
        return Result.success("签到成功！获得 " + earned + " 积分（连签 " + streak + " 天）");
    }

    // ==================== 流水 ====================

    @Override
    public List<PointsRecord> getRecords(Long userId) {
        return pointsRecordMapper.selectList(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT 200"));
    }

    // ==================== 下单抵扣 ====================

    @Override
    public PointsDeduction previewDeduction(Long userId, BigDecimal goodsAmount) {
        if (!enabled || userId == null || goodsAmount == null || goodsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PointsDeduction.ZERO;
        }
        MemberPoints account = memberPointsMapper.selectOne(new QueryWrapper<MemberPoints>()
                .eq("user_id", userId).last("LIMIT 1"));
        if (account == null || account.getPoints() <= 0) {
            return PointsDeduction.ZERO;
        }
        return calcDeduction(account.getPoints(), goodsAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsDeduction deductForOrder(Long userId, Long orderId, BigDecimal goodsAmount) {
        PointsDeduction preview = previewDeduction(userId, goodsAmount);
        if (preview.pointsUsed() <= 0) {
            return PointsDeduction.ZERO;
        }
        // 原子扣减：points >= amount 才成功，防并发透支
        int rows = memberPointsMapper.deductPoints(userId, preview.pointsUsed());
        if (rows == 0) {
            log.warn("积分抵扣并发失败: userId={}, points={}", userId, preview.pointsUsed());
            return PointsDeduction.ZERO;
        }
        insertRecord(userId, -preview.pointsUsed(), PointsRecord.TYPE_DEDUCT_ORDER, orderId,
                "下单积分抵扣 ¥" + preview.deductionAmount());
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrderDeduction(Long userId, Long orderId) {
        // 找到本单的抵扣流水（幂等：已返还过则跳过）
        PointsRecord deduct = pointsRecordMapper.selectOne(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId)
                .eq("type", PointsRecord.TYPE_DEDUCT_ORDER)
                .eq("related_id", orderId)
                .last("LIMIT 1"));
        if (deduct == null) {
            return;
        }
        Long refunded = pointsRecordMapper.selectCount(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId)
                .eq("type", PointsRecord.TYPE_REFUND_ORDER)
                .eq("related_id", orderId));
        if (refunded != null && refunded > 0) {
            return;
        }
        MemberPoints account = getOrCreateAccount(userId);
        account.setPoints(account.getPoints() - deduct.getChangeAmount()); // changeAmount 为负
        memberPointsMapper.updateById(account);
        insertRecord(userId, -deduct.getChangeAmount(), PointsRecord.TYPE_REFUND_ORDER, orderId,
                "订单取消，返还抵扣积分");
    }

    /** 可用积分 & 抵扣上限（商品金额×比例）取小，折算成整百分抵扣 */
    private PointsDeduction calcDeduction(int availablePoints, BigDecimal goodsAmount) {
        BigDecimal maxDeduction = goodsAmount.multiply(maxDeductionRatio);
        int maxUsablePoints = maxDeduction.multiply(BigDecimal.valueOf(pointsPerYuan))
                .setScale(0, RoundingMode.FLOOR).intValue();
        int usable = Math.min(availablePoints, maxUsablePoints);
        if (usable <= 0) {
            return PointsDeduction.ZERO;
        }
        // 抵扣金额向下取整到分，再反推实际消耗积分，保证"积分-金额"严格对账
        BigDecimal deduction = BigDecimal.valueOf(usable)
                .divide(BigDecimal.valueOf(pointsPerYuan), 2, RoundingMode.FLOOR);
        if (deduction.compareTo(new BigDecimal("0.01")) < 0) {
            return PointsDeduction.ZERO;
        }
        int actualPoints = deduction.multiply(BigDecimal.valueOf(pointsPerYuan))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return new PointsDeduction(actualPoints, deduction);
    }

    // ==================== 下单返积分 + 成长值 + 等级 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onOrderPaid(Long userId, Long orderId, BigDecimal payAmount) {
        if (!enabled || userId == null || orderId == null) {
            return;
        }
        // 幂等：同订单只返一次
        Long exists = pointsRecordMapper.selectCount(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId)
                .eq("type", PointsRecord.TYPE_EARN_ORDER)
                .eq("related_id", orderId));
        if (exists != null && exists > 0) {
            return;
        }
        MemberPoints account = getOrCreateAccount(userId);
        int earned = 0;
        if (payAmount != null && earnPerYuan > 0) {
            earned = payAmount.setScale(0, RoundingMode.FLOOR).intValue() * earnPerYuan;
        }
        BigDecimal growth = account.getGrowth().add(payAmount == null ? BigDecimal.ZERO : payAmount);
        int newLevel = levelOf(growth);
        boolean levelUp = newLevel > account.getLevel();

        if (earned > 0) {
            account.setPoints(account.getPoints() + earned);
            account.setTotalEarned(account.getTotalEarned() + earned);
        }
        account.setGrowth(growth);
        account.setLevel(newLevel);
        memberPointsMapper.updateById(account);

        if (earned > 0) {
            insertRecord(userId, earned, PointsRecord.TYPE_EARN_ORDER, orderId,
                    "订单消费返积分（实付 ¥" + payAmount + "）");
            notifyUser(userId, "积分到账", "本单消费获得 " + earned + " 积分，当前可用 " + account.getPoints() + " 积分！",
                    orderId);
        }
        if (levelUp) {
            notifyUser(userId, "🎉 会员等级提升",
                    "恭喜升级为「" + levelName(newLevel) + "」，下单立享 "
                            + levelDiscountText(newLevel) + " 专属折扣！", null);
        }
    }

    // ==================== 评价晒单 ====================

    @Override
    public void onReviewCreated(Long userId, Long reviewId) {
        if (!enabled || userId == null || reviewId == null) {
            return;
        }
        // 幂等：同评价只返一次
        Long exists = pointsRecordMapper.selectCount(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId)
                .eq("type", PointsRecord.TYPE_REVIEW)
                .eq("related_id", reviewId));
        if (exists != null && exists > 0) {
            return;
        }
        // Redis 日计数器防刷（每天最多 N 条评价返积分）
        String counterKey = "points:review:daily:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        Long count = stringRedisTemplate.opsForValue().increment(counterKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(counterKey, 2, TimeUnit.DAYS);
        }
        if (count != null && count > reviewDailyLimit) {
            log.info("评价返积分触发每日上限: userId={}, count={}", userId, count);
            return;
        }
        MemberPoints account = getOrCreateAccount(userId);
        account.setPoints(account.getPoints() + reviewPoints);
        account.setTotalEarned(account.getTotalEarned() + reviewPoints);
        memberPointsMapper.updateById(account);
        insertRecord(userId, reviewPoints, PointsRecord.TYPE_REVIEW, reviewId, "评价晒单奖励");
        notifyUser(userId, "评价奖励", "感谢您的真实评价，+" + reviewPoints + " 积分已到账！", null);
    }

    // ==================== 完善画像（联动 UserProfile 冷启动） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> claimProfileBonus(Long userId) {
        if (!enabled) {
            return Result.error(400, "积分功能未开启");
        }
        Long claimed = pointsRecordMapper.selectCount(new QueryWrapper<PointsRecord>()
                .eq("user_id", userId).eq("type", PointsRecord.TYPE_PROFILE));
        if (claimed != null && claimed > 0) {
            return Result.error(400, "画像奖励已领取过啦");
        }
        // 触发画像重算，检验完整度（有消费层级 + 偏好品类才算"完善"）
        userProfileService.recalculateProfile(userId);
        UserProfile profile = userProfileService.getProfile(userId);
        boolean complete = profile != null
                && profile.getUserTier() != null
                && !"NEW".equals(profile.getUserTier())
                && profile.getPreferredCategories() != null
                && !profile.getPreferredCategories().isBlank();
        if (!complete) {
            return Result.error(400, "画像还不够完善哦～先完成一笔订单或浏览收藏一些商品，再来领取奖励吧！");
        }
        MemberPoints account = getOrCreateAccount(userId);
        account.setPoints(account.getPoints() + profileBonus);
        account.setTotalEarned(account.getTotalEarned() + profileBonus);
        memberPointsMapper.updateById(account);
        insertRecord(userId, profileBonus, PointsRecord.TYPE_PROFILE, userId, "完善消费画像奖励");
        notifyUser(userId, "画像奖励", "完善消费画像成功，+" + profileBonus + " 积分！AI 导购将更懂你～", null);
        return Result.success("领取成功！+" + profileBonus + " 积分");
    }

    // ==================== 积分兑换优惠券 ====================

    @Override
    public List<Coupon> getExchangeableCoupons() {
        return couponMapper.selectList(new QueryWrapper<Coupon>()
                .isNotNull("points_price")
                .gt("points_price", 0)
                .eq("status", 1)
                .orderByAsc("points_price"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> exchangeCoupon(Long userId, Long couponId) {
        if (!enabled) {
            return Result.error(400, "积分功能未开启");
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getPointsPrice() == null || coupon.getPointsPrice() <= 0) {
            return Result.error(400, "该优惠券不支持积分兑换");
        }
        if (coupon.getStatus() == null || coupon.getStatus() != 1) {
            return Result.error(400, "该优惠券不在兑换期内");
        }
        if (coupon.getRemainCount() == null || coupon.getRemainCount() <= 0) {
            return Result.error(400, "该优惠券已被兑完啦");
        }
        MemberPoints account = getOrCreateAccount(userId);
        if (account.getPoints() < coupon.getPointsPrice()) {
            return Result.error(400, "积分不足，还差 " + (coupon.getPointsPrice() - account.getPoints()) + " 积分");
        }
        // 乐观锁扣减库存（@Version），失败=被抢完
        coupon.setRemainCount(coupon.getRemainCount() - 1);
        if (couponMapper.updateById(coupon) == 0) {
            return Result.error(500, "手慢了，请重试");
        }
        // 原子扣积分
        if (memberPointsMapper.deductPoints(userId, coupon.getPointsPrice()) == 0) {
            throw new RuntimeException("积分扣减失败（并发）");
        }
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0);
        userCoupon.setCreateTime(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);

        insertRecord(userId, -coupon.getPointsPrice(), PointsRecord.TYPE_EXCHANGE_COUPON, couponId,
                "积分兑换优惠券「" + coupon.getName() + "」");
        notifyUser(userId, "兑换成功",
                "已用 " + coupon.getPointsPrice() + " 积分兑换「" + coupon.getName() + "」，去我的优惠券查看吧！", null);
        return Result.success("兑换成功！「" + coupon.getName() + "」已放入我的优惠券");
    }

    // ==================== 等级 ====================

    @Override
    public BigDecimal getLevelDiscount(Long userId) {
        if (!enabled || userId == null) {
            return BigDecimal.ZERO;
        }
        MemberPoints account = memberPointsMapper.selectOne(new QueryWrapper<MemberPoints>()
                .eq("user_id", userId).last("LIMIT 1"));
        if (account == null) {
            return BigDecimal.ZERO;
        }
        return levelDiscount(account.getLevel());
    }

    @Override
    public String getMemberLevelPrompt(Long userId) {
        if (!enabled || userId == null) {
            return "";
        }
        try {
            MemberPoints account = memberPointsMapper.selectOne(new QueryWrapper<MemberPoints>()
                    .eq("user_id", userId).last("LIMIT 1"));
            if (account == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【会员信息】等级：").append(levelName(account.getLevel()));
            BigDecimal discount = levelDiscount(account.getLevel());
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("（下单立享").append(levelDiscountText(account.getLevel())).append("专属折扣）");
            }
            sb.append("，当前可用积分：").append(account.getPoints())
                    .append("（").append(pointsPerYuan).append("积分可抵1元）");
            sb.append("，累计消费：¥").append(account.getGrowth().setScale(0, RoundingMode.FLOOR));
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建会员等级提示词失败: userId={}, {}", userId, e.getMessage());
            return "";
        }
    }

    private BigDecimal[] growthThresholds() {
        return Arrays.stream(levelGrowthConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BigDecimal::new)
                .toArray(BigDecimal[]::new);
    }

    private String[] levelNames() {
        return Arrays.stream(levelNamesConfig.split(",")).map(String::trim).toArray(String[]::new);
    }

    private BigDecimal[] levelDiscounts() {
        return Arrays.stream(levelDiscountsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BigDecimal::new)
                .toArray(BigDecimal[]::new);
    }

    /** 成长值 → 等级（1-based） */
    private int levelOf(BigDecimal growth) {
        BigDecimal[] thresholds = growthThresholds();
        int level = 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (growth.compareTo(thresholds[i]) >= 0) {
                level = i + 1;
            }
        }
        return level;
    }

    private String levelName(int level) {
        String[] names = levelNames();
        int idx = Math.min(Math.max(level - 1, 0), names.length - 1);
        return names[idx];
    }

    private BigDecimal levelDiscount(int level) {
        BigDecimal[] discounts = levelDiscounts();
        int idx = Math.min(Math.max(level - 1, 0), discounts.length - 1);
        return discounts[idx];
    }

    /** 0.02 → "98折" */
    private String levelDiscountText(int level) {
        BigDecimal discount = levelDiscount(level);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        BigDecimal zhe = BigDecimal.TEN.subtract(discount.multiply(BigDecimal.TEN))
                .stripTrailingZeros();
        return zhe.toPlainString() + "折";
    }

    private void insertRecord(Long userId, int changeAmount, String type, Long relatedId, String description) {
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setChangeAmount(changeAmount);
        record.setType(type);
        record.setRelatedId(relatedId);
        record.setDescription(description);
        record.setCreateTime(LocalDateTime.now());
        pointsRecordMapper.insert(record);
    }

    private void notifyUser(Long userId, String title, String content, Long orderId) {
        eventPublisher.publishEvent(new NotificationEvent(this,
                userId,
                NotificationType.POINTS.getCode(),
                title,
                content,
                orderId != null ? NotificationTargetType.ORDER.getCode() : NotificationTargetType.NONE.getCode(),
                orderId));
    }
}
