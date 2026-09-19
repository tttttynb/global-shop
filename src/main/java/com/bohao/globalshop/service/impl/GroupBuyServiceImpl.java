package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.GroupBuyCreateDto;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.enums.NotificationTargetType;
import com.bohao.globalshop.enums.NotificationType;
import com.bohao.globalshop.event.NotificationEvent;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.GroupBuyService;
import com.bohao.globalshop.service.OrderService;
import com.bohao.globalshop.service.SkuService;
import com.bohao.globalshop.vo.GroupBuyActivityVo;
import com.bohao.globalshop.vo.GroupRecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 社交拼团实现（Phase 4 - F7）
 * <p>
 * 关键设计：
 * - 参团下单复用 OrderService 全链路（SKU 库存扣减 / 运费税费 / MQ 超时取消 / 支付网关），
 *   仅通过 groupRecordId 切换到拼团价 —— 与直播秒杀 orderSource 标记同构；
 * - 成团判定用条件 UPDATE 原子推进（status=0 → 1），并发支付不会重复成团；
 * - 超时未成团：未支付成员订单走 cancelSingleOrder（回补库存），已支付成员自动退款
 *   （退款余额 + 回补 SKU 库存 + 落 refund_order，与 RefundService.approveRefund 同构）；
 * - 风控：活动级每人限参（DB 计数）+ 每日参团上限（Redis 计数器）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyServiceImpl implements GroupBuyService {

    private final GroupBuyActivityMapper activityMapper;
    private final GroupBuyRecordMapper recordMapper;
    private final GroupBuyMemberMapper memberMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final SkuService skuService;
    private final OrderService orderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** 每人每日参团上限（Redis 计数防刷） */
    @Value("${app.group-buy.daily-join-limit:5}")
    private int dailyJoinLimit;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 商家侧 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> createActivity(Long merchantUserId, GroupBuyCreateDto dto) {
        Shop shop = getShopByUserId(merchantUserId);
        if (shop == null) {
            return Result.error(403, "您还不是商家，无法创建拼团活动！");
        }
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null || product.getStatus() != 1) {
            return Result.error(400, "商品不存在或已下架！");
        }
        if (!shop.getId().equals(product.getShopId())) {
            return Result.error(403, "无权为该商品创建拼团活动！");
        }
        if (dto.getGroupPrice() == null || dto.getGroupPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "拼团价必须大于 0！");
        }
        if (dto.getGroupPrice().compareTo(product.getPrice()) >= 0) {
            return Result.error(400, "拼团价必须低于日常售价 ¥" + product.getPrice() + "！");
        }
        int required = dto.getRequiredMembers() == null ? 3 : dto.getRequiredMembers();
        if (required < 2 || required > 10) {
            return Result.error(400, "成团人数需在 2-10 人之间！");
        }
        int validHours = dto.getValidHours() == null ? 24 : dto.getValidHours();
        if (validHours < 1 || validHours > 168) {
            return Result.error(400, "有效期需在 1-168 小时之间！");
        }

        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setProductId(product.getId());
        activity.setSkuId(dto.getSkuId());
        activity.setShopId(shop.getId());
        activity.setGroupPrice(dto.getGroupPrice());
        activity.setRequiredMembers(required);
        activity.setValidHours(validHours);
        activity.setActivityStock(0);
        activity.setSoldCount(0);
        activity.setPerUserLimit(dto.getPerUserLimit() == null || dto.getPerUserLimit() < 1 ? 1 : dto.getPerUserLimit());
        activity.setStatus(1);
        activityMapper.insert(activity);
        log.info("🛒 拼团活动创建成功: activityId={}, product={}, groupPrice={}, {}人团",
                activity.getId(), product.getName(), activity.getGroupPrice(), required);
        return Result.success(String.valueOf(activity.getId()));
    }

    @Override
    public Result<List<GroupBuyActivity>> getShopActivities(Long merchantUserId) {
        Shop shop = getShopByUserId(merchantUserId);
        if (shop == null) {
            return Result.error(403, "您还不是商家！");
        }
        return Result.success(activityMapper.selectList(new QueryWrapper<GroupBuyActivity>()
                .eq("shop_id", shop.getId())
                .orderByDesc("create_time")));
    }

    @Override
    public Result<String> toggleActivity(Long merchantUserId, Long activityId, Integer status) {
        Shop shop = getShopByUserId(merchantUserId);
        GroupBuyActivity activity = activityMapper.selectById(activityId);
        if (shop == null || activity == null || !shop.getId().equals(activity.getShopId())) {
            return Result.error(403, "无权操作此活动！");
        }
        activity.setStatus(status != null && status == 1 ? 1 : 0);
        activityMapper.updateById(activity);
        return Result.success(activity.getStatus() == 1 ? "活动已开启" : "活动已结束");
    }

    // ==================== 买家侧 ====================

    @Override
    public Result<List<GroupBuyActivityVo>> listActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<GroupBuyActivity> activities = activityMapper.selectList(new QueryWrapper<GroupBuyActivity>()
                .eq("status", 1)
                .and(w -> w.isNull("start_time").or().le("start_time", now))
                .and(w -> w.isNull("end_time").or().ge("end_time", now))
                .orderByDesc("create_time"));
        List<GroupBuyActivityVo> voList = new ArrayList<>();
        for (GroupBuyActivity activity : activities) {
            GroupBuyActivityVo vo = buildActivityVo(activity);
            if (vo != null) {
                voList.add(vo);
            }
        }
        return Result.success(voList);
    }

    @Override
    public Result<GroupBuyActivityVo> getActivityDetail(Long activityId) {
        GroupBuyActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) {
            return Result.error(404, "拼团活动不存在或已结束！");
        }
        GroupBuyActivityVo vo = buildActivityVo(activity);
        return vo == null ? Result.error(404, "商品已下架！") : Result.success(vo);
    }

    @Override
    public Result<List<GroupRecordVo>> getOngoingGroups(Long activityId) {
        List<GroupBuyRecord> records = recordMapper.selectList(new QueryWrapper<GroupBuyRecord>()
                .eq("activity_id", activityId)
                .eq("status", 0)
                .gt("expire_time", LocalDateTime.now())
                .orderByAsc("expire_time")
                .last("LIMIT 20"));
        List<GroupRecordVo> voList = new ArrayList<>();
        for (GroupBuyRecord record : records) {
            voList.add(buildRecordVo(record, null));
        }
        return Result.success(voList);
    }

    @Override
    public GroupBuyActivityVo getProductActivity(Long productId) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyActivity activity = activityMapper.selectOne(new QueryWrapper<GroupBuyActivity>()
                .eq("product_id", productId)
                .eq("status", 1)
                .and(w -> w.isNull("start_time").or().le("start_time", now))
                .and(w -> w.isNull("end_time").or().ge("end_time", now))
                .orderByDesc("id")
                .last("LIMIT 1"));
        return activity == null ? null : buildActivityVo(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Long>> openGroup(Long userId, Long activityId, Long addressId) {
        GroupBuyActivity activity = activityMapper.selectById(activityId);
        Result<String> check = validateActivity(activity);
        if (check != null) {
            return Result.error(check.getCode(), check.getMessage());
        }
        Result<String> risk = checkRisk(userId, activity);
        if (risk != null) {
            return Result.error(risk.getCode(), risk.getMessage());
        }

        // 1. 建团实例
        GroupBuyRecord record = new GroupBuyRecord();
        record.setActivityId(activityId);
        record.setLeaderId(userId);
        record.setStatus(0);
        record.setMemberCount(0);
        record.setRequiredMembers(activity.getRequiredMembers());
        record.setExpireTime(LocalDateTime.now().plusHours(activity.getValidHours()));
        recordMapper.insert(record);

        // 2. 团长成员行
        GroupBuyMember leader = newMember(record, activity, userId, true);
        memberMapper.insert(leader);

        // 3. 按拼团价创建待支付订单（失败抛异常整体回滚）
        Long orderId = createGroupOrder(userId, activity, record.getId(), addressId);
        leader.setOrderId(orderId);
        memberMapper.updateById(leader);

        incrDailyJoin(userId);
        notifyUser(userId, "开团成功 🎉",
                "您发起了「" + productName(activity.getProductId()) + "」拼团，邀请 "
                        + (activity.getRequiredMembers() - 1) + " 位好友参团即可成团享 ¥" + activity.getGroupPrice() + "！",
                record.getId());
        Map<String, Long> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("orderId", orderId);
        return Result.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Long>> joinGroup(Long userId, Long recordId, Long addressId) {
        GroupBuyRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            return Result.error(404, "该团不存在！");
        }
        if (record.getStatus() != 0) {
            return Result.error(400, record.getStatus() == 1 ? "该团已成团，看看其他团吧！" : "该团已结束！");
        }
        if (record.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.error(400, "该团已过期，开一个新团吧！");
        }
        if (record.getMemberCount() >= record.getRequiredMembers()) {
            return Result.error(400, "该团人数已满！");
        }
        GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
        Result<String> check = validateActivity(activity);
        if (check != null) {
            return Result.error(check.getCode(), check.getMessage());
        }
        Result<String> risk = checkRisk(userId, activity);
        if (risk != null) {
            return Result.error(risk.getCode(), risk.getMessage());
        }
        Long exists = memberMapper.selectCount(new QueryWrapper<GroupBuyMember>()
                .eq("record_id", recordId).eq("user_id", userId).ne("status", 2));
        if (exists != null && exists > 0) {
            return Result.error(400, "您已经在这个团里啦，去「我的拼团」完成支付吧！");
        }

        GroupBuyMember member = newMember(record, activity, userId, false);
        memberMapper.insert(member);

        Long orderId = createGroupOrder(userId, activity, recordId, addressId);
        member.setOrderId(orderId);
        memberMapper.updateById(member);

        incrDailyJoin(userId);
        notifyUser(record.getLeaderId(), "有人参团啦！",
                "「" + productName(activity.getProductId()) + "」的团又进来 1 位小伙伴，快分享邀请更多人，还差 "
                        + Math.max(record.getRequiredMembers() - record.getMemberCount() - 1, 0) + " 人成团！",
                recordId);
        Map<String, Long> data = new HashMap<>();
        data.put("recordId", recordId);
        data.put("orderId", orderId);
        return Result.success(data);
    }

    @Override
    public Result<List<GroupRecordVo>> getMyGroups(Long userId) {
        List<GroupBuyMember> members = memberMapper.selectList(new QueryWrapper<GroupBuyMember>()
                .eq("user_id", userId)
                .orderByDesc("join_time")
                .last("LIMIT 50"));
        List<GroupRecordVo> voList = new ArrayList<>();
        for (GroupBuyMember member : members) {
            GroupBuyRecord record = recordMapper.selectById(member.getRecordId());
            if (record != null) {
                voList.add(buildRecordVo(record, userId));
            }
        }
        return Result.success(voList);
    }

    @Override
    public Result<GroupRecordVo> getRecordDetail(Long userId, Long recordId) {
        GroupBuyRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            return Result.error(404, "该团不存在！");
        }
        return Result.success(buildRecordVo(record, userId));
    }

    // ==================== 系统钩子 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onOrderPaid(Long orderId) {
        GroupBuyMember member = memberMapper.selectOne(new QueryWrapper<GroupBuyMember>()
                .eq("order_id", orderId).last("LIMIT 1"));
        if (member == null) {
            return; // 非拼团订单
        }
        // 条件更新保证幂等：只有 0→1 的赢家继续推进成团判定
        int rows = memberMapper.update(null, new UpdateWrapper<GroupBuyMember>()
                .eq("id", member.getId())
                .eq("status", 0)
                .set("status", 1)
                .set("pay_time", LocalDateTime.now()));
        if (rows == 0) {
            return;
        }
        TradeOrder order = traderOrderMapper.selectById(orderId);
        if (order != null) {
            member.setPayAmount(order.getTotalAmount());
            memberMapper.updateById(member);
        }

        GroupBuyRecord record = recordMapper.selectById(member.getRecordId());
        if (record == null || record.getStatus() != 0) {
            return;
        }
        // 原子 +1（仅拼团中的团）
        recordMapper.update(null, new UpdateWrapper<GroupBuyRecord>()
                .eq("id", record.getId())
                .eq("status", 0)
                .setSql("member_count = member_count + 1"));
        record = recordMapper.selectById(record.getId());
        if (record.getMemberCount() >= record.getRequiredMembers()) {
            int formed = recordMapper.update(null, new UpdateWrapper<GroupBuyRecord>()
                    .eq("id", record.getId())
                    .eq("status", 0)
                    .set("status", 1)
                    .set("form_time", LocalDateTime.now()));
            if (formed == 1) {
                onGroupFormed(record);
            }
        } else {
            notifyUser(member.getUserId(), "参团成功 ✅",
                    "已参团「" + productName(recordActivityProduct(record)) + "」，还差 "
                            + (record.getRequiredMembers() - record.getMemberCount()) + " 人成团，快邀请好友吧！",
                    record.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireGroups() {
        List<GroupBuyRecord> expired = recordMapper.selectList(new QueryWrapper<GroupBuyRecord>()
                .eq("status", 0)
                .lt("expire_time", LocalDateTime.now())
                .last("LIMIT 50"));
        int failed = 0;
        for (GroupBuyRecord record : expired) {
            // 条件更新抢占：与"成团判定"并发时只有一方能改写状态
            int rows = recordMapper.update(null, new UpdateWrapper<GroupBuyRecord>()
                    .eq("id", record.getId())
                    .eq("status", 0)
                    .set("status", 2));
            if (rows == 1) {
                try {
                    failGroup(record);
                    failed++;
                } catch (Exception e) {
                    log.error("拼团失败处理异常: recordId={}", record.getId(), e);
                }
            }
        }
        return failed;
    }

    /** 成团：通知全体成员 + 活动销量累计 */
    private void onGroupFormed(GroupBuyRecord record) {
        GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
        activity.setSoldCount((activity.getSoldCount() == null ? 0 : activity.getSoldCount()) + record.getMemberCount());
        activityMapper.updateById(activity);

        List<GroupBuyMember> members = memberMapper.selectList(new QueryWrapper<GroupBuyMember>()
                .eq("record_id", record.getId()).eq("status", 1));
        for (GroupBuyMember member : members) {
            notifyUser(member.getUserId(), "🎉 拼团成功！",
                    "「" + productName(activity.getProductId()) + "」" + record.getRequiredMembers()
                            + " 人团已成团！商家将尽快为您发货，跨境邮费已成功摊薄～",
                    record.getId());
        }
        log.info("🎉 拼团成团: recordId={}, activityId={}, members={}", record.getId(), record.getActivityId(), members.size());
    }

    /**
     * 超时未成团处理：
     * - 未支付成员：取消订单（cancelSingleOrder 幂等回补库存）
     * - 已支付成员：自动退款（退余额 + 回补 SKU 库存 + 落 refund_order + 订单关闭）
     */
    private void failGroup(GroupBuyRecord record) {
        GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
        List<GroupBuyMember> members = memberMapper.selectList(new QueryWrapper<GroupBuyMember>()
                .eq("record_id", record.getId()));
        for (GroupBuyMember member : members) {
            if (member.getStatus() == 2) {
                continue;
            }
            if (member.getStatus() == 0) {
                // 未支付：走标准取消（回补库存），订单可能已被 MQ 超时取消，幂等安全
                if (member.getOrderId() != null) {
                    orderService.cancelSingleOrder(member.getOrderId());
                }
                memberMapper.update(null, new UpdateWrapper<GroupBuyMember>()
                        .eq("id", member.getId()).eq("status", 0).set("status", 2));
            } else if (member.getStatus() == 1) {
                refundPaidMember(member, record, activity);
            }
        }
        log.info("⏰ 拼团超时失败: recordId={}, members={}", record.getId(), members.size());
    }

    /** 拼团失败退款（在 expireGroups 事务内执行；单团异常由调用方捕获隔离） */
    private void refundPaidMember(GroupBuyMember member, GroupBuyRecord record, GroupBuyActivity activity) {
        TradeOrder order = traderOrderMapper.selectById(member.getOrderId());
        if (order == null || order.getStatus() != 1) {
            memberMapper.update(null, new UpdateWrapper<GroupBuyMember>()
                    .eq("id", member.getId()).eq("status", 1).set("status", 2));
            return;
        }
        BigDecimal refundAmount = order.getTotalAmount();
        // 1. 订单关闭
        order.setStatus(2);
        traderOrderMapper.updateById(order);
        // 2. 退款余额（与 RefundService.approveRefund 同构）
        User buyer = userMapper.selectById(order.getUserId());
        if (buyer != null) {
            buyer.setBalance(buyer.getBalance().add(refundAmount));
            userMapper.updateById(buyer);
        }
        // 3. 退款单落库（status=3 直接成功）
        RefundOrder refund = new RefundOrder();
        refund.setOrderId(order.getId());
        refund.setUserId(order.getUserId());
        refund.setShopId(order.getShopId());
        refund.setRefundAmount(refundAmount);
        refund.setReason("拼团未成团，系统自动退款");
        refund.setStatus(3);
        refundOrderMapper.insert(refund);
        // 4. 回补 SKU 库存
        List<TradeOrderItem> items = tradeOrderItemMapper.selectList(new QueryWrapper<TradeOrderItem>()
                .eq("order_id", order.getId()));
        for (TradeOrderItem item : items) {
            refund.setOrderItemId(item.getId());
            if (item.getSkuId() != null) {
                skuService.restoreStock(item.getSkuId(), item.getQuantity());
                skuService.restoreRedisStock(item.getSkuId(), item.getQuantity());
                skuService.syncProductAggregate(item.getProductId());
            } else {
                Product product = productMapper.selectById(item.getProductId());
                if (product != null) {
                    product.setStock(product.getStock() + item.getQuantity());
                    productMapper.updateById(product);
                }
            }
        }
        refundOrderMapper.updateById(refund);
        // 5. 成员状态 + 通知
        memberMapper.update(null, new UpdateWrapper<GroupBuyMember>()
                .eq("id", member.getId()).eq("status", 1).set("status", 2));
        notifyUser(member.getUserId(), "拼团未成团，已自动退款",
                "很遗憾，「" + (activity != null ? productName(activity.getProductId()) : "拼团商品")
                        + "」的团超时未成团，¥" + refundAmount + " 已退回您的余额，库存已释放。",
                record.getId());
    }

    // ==================== 内部工具 ====================

    /** 按拼团价走标准下单链路；失败抛异常回滚整个开团/参团事务 */
    private Long createGroupOrder(Long userId, GroupBuyActivity activity, Long recordId, Long addressId) {
        OrderCreateDto dto = new OrderCreateDto();
        dto.setProductId(activity.getProductId());
        dto.setSkuId(activity.getSkuId());
        dto.setQuantity(1);
        dto.setAddressId(addressId);
        dto.setGroupRecordId(recordId);
        Result<String> result = orderService.createOrder(userId, dto);
        if (result.getCode() != 200) {
            throw new RuntimeException(result.getMessage());
        }
        return Long.valueOf(result.getData());
    }

    /** 活动有效性校验，null=通过 */
    private Result<String> validateActivity(GroupBuyActivity activity) {
        if (activity == null || activity.getStatus() != 1) {
            return Result.error(404, "拼团活动不存在或已结束！");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && activity.getStartTime().isAfter(now)) {
            return Result.error(400, "活动还没开始哦！");
        }
        if (activity.getEndTime() != null && activity.getEndTime().isBefore(now)) {
            return Result.error(400, "活动已截止！");
        }
        Product product = productMapper.selectById(activity.getProductId());
        if (product == null || product.getStatus() != 1) {
            return Result.error(400, "拼团商品已下架！");
        }
        return null;
    }

    /** 风控：活动级每人限参 + 每日参团上限（Redis 计数），null=通过 */
    private Result<String> checkRisk(Long userId, GroupBuyActivity activity) {
        Long joined = memberMapper.selectCount(new QueryWrapper<GroupBuyMember>()
                .eq("activity_id", activity.getId())
                .eq("user_id", userId)
                .ne("status", 2));
        if (joined != null && joined >= activity.getPerUserLimit()) {
            return Result.error(400, "该活动每人限参 " + activity.getPerUserLimit() + " 次，您已达上限！");
        }
        String dailyKey = "group:join:daily:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        String countStr = stringRedisTemplate.opsForValue().get(dailyKey);
        if (countStr != null && Integer.parseInt(countStr) >= dailyJoinLimit) {
            return Result.error(400, "今日参团次数已达上限（" + dailyJoinLimit + " 次），明天再来吧！");
        }
        return null;
    }

    private void incrDailyJoin(Long userId) {
        String dailyKey = "group:join:daily:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        Long count = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(dailyKey, 2, TimeUnit.DAYS);
        }
    }

    private GroupBuyMember newMember(GroupBuyRecord record, GroupBuyActivity activity, Long userId, boolean leader) {
        GroupBuyMember member = new GroupBuyMember();
        member.setRecordId(record.getId());
        member.setActivityId(activity.getId());
        member.setUserId(userId);
        member.setPayAmount(activity.getGroupPrice());
        member.setIsLeader(leader ? 1 : 0);
        member.setStatus(0);
        member.setJoinTime(LocalDateTime.now());
        return member;
    }

    private GroupBuyActivityVo buildActivityVo(GroupBuyActivity activity) {
        Product product = productMapper.selectById(activity.getProductId());
        if (product == null || product.getStatus() != 1) {
            return null;
        }
        GroupBuyActivityVo vo = new GroupBuyActivityVo();
        vo.setActivityId(activity.getId());
        vo.setProductId(product.getId());
        vo.setProductName(product.getName());
        vo.setCoverImage(product.getCoverImage());
        Shop shop = activity.getShopId() != null ? shopMapper.selectById(activity.getShopId()) : null;
        vo.setShopName(shop != null ? shop.getName() : "平台自营店");
        vo.setOriginalPrice(product.getPrice());
        vo.setGroupPrice(activity.getGroupPrice());
        vo.setRequiredMembers(activity.getRequiredMembers());
        vo.setValidHours(activity.getValidHours());
        vo.setSoldCount(activity.getSoldCount());
        vo.setEndTime(activity.getEndTime());
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            vo.setDiscountRate(activity.getGroupPrice().divide(product.getPrice(), 2, RoundingMode.HALF_UP));
        }
        vo.setOngoingGroups(recordMapper.selectCount(new QueryWrapper<GroupBuyRecord>()
                .eq("activity_id", activity.getId())
                .eq("status", 0)
                .gt("expire_time", LocalDateTime.now())));
        return vo;
    }

    private GroupRecordVo buildRecordVo(GroupBuyRecord record, Long currentUserId) {
        GroupRecordVo vo = new GroupRecordVo();
        vo.setRecordId(record.getId());
        vo.setActivityId(record.getActivityId());
        vo.setStatus(record.getStatus());
        vo.setMemberCount(record.getMemberCount());
        vo.setRequiredMembers(record.getRequiredMembers());
        vo.setMissingMembers(record.getStatus() == 0
                ? Math.max(record.getRequiredMembers() - record.getMemberCount(), 0) : 0);
        vo.setExpireTime(record.getExpireTime());
        vo.setFormTime(record.getFormTime());

        GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
        if (activity != null) {
            vo.setProductId(activity.getProductId());
            vo.setGroupPrice(activity.getGroupPrice());
            Product product = productMapper.selectById(activity.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setCoverImage(product.getCoverImage());
                vo.setOriginalPrice(product.getPrice());
                Shop shop = activity.getShopId() != null ? shopMapper.selectById(activity.getShopId()) : null;
                vo.setShopName(shop != null ? shop.getName() : "平台自营店");
            }
        }

        List<GroupBuyMember> members = memberMapper.selectList(new QueryWrapper<GroupBuyMember>()
                .eq("record_id", record.getId())
                .orderByDesc("is_leader")
                .orderByAsc("join_time"));
        List<GroupRecordVo.MemberVo> memberVos = new ArrayList<>();
        for (GroupBuyMember member : members) {
            GroupRecordVo.MemberVo mvo = new GroupRecordVo.MemberVo();
            mvo.setUserId(member.getUserId());
            mvo.setIsLeader(member.getIsLeader());
            mvo.setStatus(member.getStatus());
            mvo.setJoinTime(member.getJoinTime());
            User user = userMapper.selectById(member.getUserId());
            if (user != null) {
                mvo.setNickname(user.getNickname() != null ? user.getNickname() : user.getUsername());
                mvo.setAvatar(user.getAvatar());
            }
            memberVos.add(mvo);
            if (currentUserId != null && currentUserId.equals(member.getUserId())) {
                vo.setMyStatus(member.getStatus() == 1 ? "PAID" : (member.getStatus() == 0 ? "JOINED" : "CLOSED"));
            }
        }
        // 成团人数坑位展示：前端按 requiredMembers 渲染头像占位
        vo.setMembers(memberVos);
        return vo;
    }

    private Long recordActivityProduct(GroupBuyRecord record) {
        GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
        return activity != null ? activity.getProductId() : null;
    }

    private String productName(Long productId) {
        if (productId == null) {
            return "拼团商品";
        }
        Product product = productMapper.selectById(productId);
        return product != null ? product.getName() : "拼团商品";
    }

    private Shop getShopByUserId(Long userId) {
        return shopMapper.selectOne(new QueryWrapper<Shop>().eq("user_id", userId).last("LIMIT 1"));
    }

    private void notifyUser(Long userId, String title, String content, Long recordId) {
        eventPublisher.publishEvent(new NotificationEvent(this,
                userId,
                NotificationType.GROUP_BUY.getCode(),
                title,
                content,
                recordId != null ? NotificationTargetType.GROUP_RECORD.getCode() : NotificationTargetType.NONE.getCode(),
                recordId));
    }
}
