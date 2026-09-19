package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员积分账户（Phase 4 - F8）：积分 + 成长值 + 等级
 */
@Data
@TableName("member_points")
public class MemberPoints {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（唯一） */
    private Long userId;

    /** 当前可用积分 */
    private Integer points;

    /** 累计获得积分 */
    private Integer totalEarned;

    /** 成长值（累计消费额，决定等级） */
    private BigDecimal growth;

    /** 会员等级 1青铜 2白银 3黄金 4钻石 */
    private Integer level;

    /** 连续签到天数 */
    private Integer consecutiveDays;

    /** 最近签到日期 */
    private LocalDate lastSignDate;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
