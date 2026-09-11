package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户画像管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 查看当前登录用户的消费画像
     */
    @GetMapping("/my")
    public Result<UserProfile> getMyProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        UserProfile profile = userProfileService.getProfile(userId);
        return Result.success(profile);
    }

    /**
     * 手动触发当前用户画像重算
     */
    @PostMapping("/recalculate")
    public Result<String> recalculateMyProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        userProfileService.recalculateProfile(userId);
        UserProfile profile = userProfileService.getProfile(userId);
        return Result.success("画像重算完成！当前层级: " + profile.getUserTier()
                + "，累计消费: $" + profile.getTotalSpent()
                + "，客单价: $" + profile.getAvgOrderValue());
    }

    /**
     * 🚀 一次性初始化所有存量用户的画像
     * <p>
     * 调用方式: POST /api/user/profile/init-all
     * 需要在 Header 中携带有效的 JWT Token
     */
    @PostMapping("/init-all")
    public Result<String> initAllProfiles() {
        log.info("收到存量用户画像初始化请求...");
        long start = System.currentTimeMillis();
        try {
            userProfileService.initProfilesForExistingUsers();
            long elapsed = System.currentTimeMillis() - start;
            return Result.success("存量用户画像初始化完成！耗时 " + elapsed + " ms，请查看应用日志获取详情。");
        } catch (Exception e) {
            log.error("存量画像初始化失败", e);
            return Result.error(500, "初始化失败: " + e.getMessage());
        }
    }

    /**
     * 根据 userId 查看任意用户的画像摘要（管理用）
     */
    @GetMapping("/summary/{userId}")
    public Result<Map<String, Object>> getProfileSummary(@PathVariable Long userId) {
        UserProfile profile = userProfileService.getProfile(userId);
        Map<String, Object> summary = Map.of(
                "userId", profile.getUserId(),
                "userTier", profile.getUserTier(),
                "totalSpent", profile.getTotalSpent(),
                "totalOrderCount", profile.getTotalOrderCount(),
                "avgOrderValue", profile.getAvgOrderValue(),
                "recent90dSpent", profile.getRecent90dSpent(),
                "priceRange", (profile.getPriceRangeMin() != null ? profile.getPriceRangeMin() : "N/A")
                        + " - " + (profile.getPriceRangeMax() != null ? profile.getPriceRangeMax() : "N/A"),
                "activityScore", profile.getActivityScore()
        );
        return Result.success(summary);
    }
}
