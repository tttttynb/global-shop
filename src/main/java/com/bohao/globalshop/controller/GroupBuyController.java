package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.GroupBuyCreateDto;
import com.bohao.globalshop.entity.GroupBuyActivity;
import com.bohao.globalshop.service.GroupBuyService;
import com.bohao.globalshop.vo.GroupBuyActivityVo;
import com.bohao.globalshop.vo.GroupRecordVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 社交拼团（Phase 4 - F7）
 * <p>
 * 公开浏览：拼团专区 / 活动详情 / 进行中的团 / 商品页拼团入口；
 * 登录操作：开团 / 参团 / 我的拼团 / 团详情（WebConfig 拦截器保护）；
 * 商家管理：/merchant/** 创建与上下架活动。
 */
@RestController
@RequestMapping("/api/group-buy")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    // ==================== 买家侧（公开浏览） ====================

    /** 拼团专区：进行中的活动列表 */
    @GetMapping("/activities")
    public Result<List<GroupBuyActivityVo>> listActivities() {
        return groupBuyService.listActivities();
    }

    /** 活动详情 */
    @GetMapping("/activity/{id}")
    public Result<GroupBuyActivityVo> getActivityDetail(@PathVariable("id") Long activityId) {
        return groupBuyService.getActivityDetail(activityId);
    }

    /** 某活动下进行中的团（"N 个团在拼，可直接参团"） */
    @GetMapping("/activity/{id}/groups")
    public Result<List<GroupRecordVo>> getOngoingGroups(@PathVariable("id") Long activityId) {
        return groupBuyService.getOngoingGroups(activityId);
    }

    /** 商品页拼团入口：该商品当前生效的拼团活动（无则 data=null） */
    @GetMapping("/product/{productId}")
    public Result<GroupBuyActivityVo> getProductActivity(@PathVariable("productId") Long productId) {
        return Result.success(groupBuyService.getProductActivity(productId));
    }

    // ==================== 买家侧（需登录） ====================

    /** 开团：返回 {recordId, orderId}，前端跳转收银台 */
    @PostMapping("/open/{activityId}")
    public Result<Map<String, Long>> openGroup(HttpServletRequest request,
                                               @PathVariable("activityId") Long activityId,
                                               @RequestParam(value = "addressId", required = false) Long addressId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.openGroup(userId, activityId, addressId);
    }

    /** 参团：返回 {recordId, orderId}，前端跳转收银台 */
    @PostMapping("/join/{recordId}")
    public Result<Map<String, Long>> joinGroup(HttpServletRequest request,
                                               @PathVariable("recordId") Long recordId,
                                               @RequestParam(value = "addressId", required = false) Long addressId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.joinGroup(userId, recordId, addressId);
    }

    /** 我的拼团 */
    @GetMapping("/my")
    public Result<List<GroupRecordVo>> getMyGroups(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.getMyGroups(userId);
    }

    /** 团详情（成员列表 + 还差 N 人 + 倒计时，分享落地页） */
    @GetMapping("/record/{id}")
    public Result<GroupRecordVo> getRecordDetail(HttpServletRequest request, @PathVariable("id") Long recordId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.getRecordDetail(userId, recordId);
    }

    // ==================== 商家侧 ====================

    @PostMapping("/merchant/activity")
    public Result<String> createActivity(HttpServletRequest request, @RequestBody GroupBuyCreateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.createActivity(userId, dto);
    }

    @GetMapping("/merchant/activities")
    public Result<List<GroupBuyActivity>> getShopActivities(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.getShopActivities(userId);
    }

    @PostMapping("/merchant/activity/{id}/status")
    public Result<String> toggleActivity(HttpServletRequest request,
                                         @PathVariable("id") Long activityId,
                                         @RequestParam Integer status) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return groupBuyService.toggleActivity(userId, activityId, status);
    }
}
