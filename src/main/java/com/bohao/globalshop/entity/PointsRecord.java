package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水（Phase 4 - F8）：每笔积分变动可追溯
 */
@Data
@TableName("points_record")
public class PointsRecord {

    /** 积分类型：下单返积分 */
    public static final String TYPE_EARN_ORDER = "EARN_ORDER";
    /** 积分类型：每日签到 */
    public static final String TYPE_SIGN_IN = "SIGN_IN";
    /** 积分类型：评价晒单 */
    public static final String TYPE_REVIEW = "REVIEW";
    /** 积分类型：完善画像 */
    public static final String TYPE_PROFILE = "PROFILE";
    /** 积分类型：下单抵扣 */
    public static final String TYPE_DEDUCT_ORDER = "DEDUCT_ORDER";
    /** 积分类型：兑换优惠券 */
    public static final String TYPE_EXCHANGE_COUPON = "EXCHANGE_COUPON";
    /** 积分类型：订单取消返还抵扣 */
    public static final String TYPE_REFUND_ORDER = "REFUND_ORDER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 积分变动（正=获得，负=消耗） */
    private Integer changeAmount;

    /** EARN_ORDER / SIGN_IN / REVIEW / PROFILE / DEDUCT_ORDER / EXCHANGE_COUPON / REFUND_ORDER */
    private String type;

    /** 关联ID（订单/优惠券/画像） */
    private Long relatedId;

    private String description;

    private LocalDateTime createTime;
}
