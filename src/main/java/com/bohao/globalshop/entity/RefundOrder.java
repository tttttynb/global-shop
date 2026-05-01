package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_order")
public class RefundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long shopId;
    private BigDecimal refundAmount;
    private String reason;
    private String images;
    private Integer status; // 0-待审核 1-商家同意 2-商家拒绝 3-退款成功 4-已取消
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
