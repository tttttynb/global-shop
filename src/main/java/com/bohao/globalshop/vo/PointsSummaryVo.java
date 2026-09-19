package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 积分中心概览（Phase 4 - F8）
 */
@Data
public class PointsSummaryVo {
    private Integer points;
    private Integer totalEarned;
    private BigDecimal growth;
    private Integer level;
    /** 青铜会员/白银会员/黄金会员/钻石会员 */
    private String levelName;
    /** 当前等级折扣率（0.02 = 98折） */
    private BigDecimal levelDiscount;
    /** 距下一等级还需消费 */
    private BigDecimal nextLevelGap;
    /** 下一等级名称（已满级为 null） */
    private String nextLevelName;
    /** 今日是否已签到 */
    private Boolean signedToday;
    /** 连续签到天数 */
    private Integer consecutiveDays;
    /** 积分抵现比例说明：100积分 = 1元 */
    private Integer pointsPerYuan;
}
