package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拼团团实例（Phase 4 - F7）：一次开团一条记录
 */
@Data
@TableName("group_buy_record")
public class GroupBuyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 拼团活动ID */
    private Long activityId;

    /** 开团人用户ID */
    private Long leaderId;

    /** 0拼团中 1已成团 2拼团失败(超时退款) */
    private Integer status;

    /** 当前已支付成员数 */
    private Integer memberCount;

    /** 成团人数（活动快照） */
    private Integer requiredMembers;

    /** 成团截止时间 */
    private LocalDateTime expireTime;

    /** 成团时间 */
    private LocalDateTime formTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
