package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.GroupBuyCreateDto;
import com.bohao.globalshop.entity.GroupBuyActivity;
import com.bohao.globalshop.vo.GroupBuyActivityVo;
import com.bohao.globalshop.vo.GroupRecordVo;

import java.util.List;

/**
 * 社交拼团（Phase 4 - F7）
 * <p>
 * 流程：开团 → 分享链接 → 参团支付（复用 PaymentGateway）→ 成团发货 / 超时自动退款。
 * 跨境主打"拼邮费"；风控：Redis 计数限制每日参团次数 + 活动级每人限参。
 */
public interface GroupBuyService {

    // ==================== 商家侧 ====================

    Result<String> createActivity(Long merchantUserId, GroupBuyCreateDto dto);

    Result<List<GroupBuyActivity>> getShopActivities(Long merchantUserId);

    Result<String> toggleActivity(Long merchantUserId, Long activityId, Integer status);

    // ==================== 买家侧 ====================

    /** 拼团专区：进行中的活动列表 */
    Result<List<GroupBuyActivityVo>> listActivities();

    /** 活动详情（含商品快照） */
    Result<GroupBuyActivityVo> getActivityDetail(Long activityId);

    /** 某活动下进行中的团（"N 个团在拼，可直接参团"） */
    Result<List<GroupRecordVo>> getOngoingGroups(Long activityId);

    /** 商品页拼团入口：该商品当前生效的拼团活动（无则 null） */
    GroupBuyActivityVo getProductActivity(Long productId);

    /** 开团：建团实例 + 团长成员 + 按拼团价创建待支付订单，返回 {recordId, orderId} */
    Result<java.util.Map<String, Long>> openGroup(Long userId, Long activityId, Long addressId);

    /** 参团：校验风控 + 建成员 + 按拼团价创建待支付订单，返回 {recordId, orderId} */
    Result<java.util.Map<String, Long>> joinGroup(Long userId, Long recordId, Long addressId);

    /** 我的拼团（我开的 + 我参的） */
    Result<List<GroupRecordVo>> getMyGroups(Long userId);

    /** 团详情（成员列表 + 还差 N 人 + 倒计时） */
    Result<GroupRecordVo> getRecordDetail(Long userId, Long recordId);

    // ==================== 系统钩子 ====================

    /** 订单支付成功 → 成员核销 + 满员成团判定（OrderPaidListener 调用） */
    void onOrderPaid(Long orderId);

    /** 超时未成团 → 失败关团 + 已支付成员自动退款（GroupBuyExpireTask 调用） */
    int expireGroups();
}
