package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 团实例详情（Phase 4 - F7）："还差 N 人" 分享卡片数据
 */
@Data
public class GroupRecordVo {
    private Long recordId;
    private Long activityId;
    /** 0拼团中 1已成团 2拼团失败 */
    private Integer status;
    private Integer memberCount;
    private Integer requiredMembers;
    /** 还差几人成团（status=0 时 > 0） */
    private Integer missingMembers;
    private LocalDateTime expireTime;
    private LocalDateTime formTime;

    // ---- 活动/商品快照 ----
    private Long productId;
    private String productName;
    private String coverImage;
    private BigDecimal groupPrice;
    private BigDecimal originalPrice;
    private String shopName;

    // ---- 成员列表 ----
    private List<MemberVo> members;

    /** 当前登录用户在此团中的状态：null=未参团 JOINED=待支付 PAID=已支付 */
    private String myStatus;

    @Data
    public static class MemberVo {
        private Long userId;
        private String nickname;
        private String avatar;
        private Integer isLeader;
        /** 0待支付 1已支付 2已退款/已取消 */
        private Integer status;
        private LocalDateTime joinTime;
    }
}
