package com.bohao.globalshop.vo;

import com.bohao.globalshop.entity.TradeOrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVo {
    private Long id;
    private BigDecimal totalAmount;
    private Integer status;
    private BigDecimal discountAmount;
    /** 🆕 国际运费（Phase 3 - F6） */
    private BigDecimal shippingFee;
    /** 🆕 跨境税费（Phase 3 - F6） */
    private BigDecimal taxFee;
    /** 🆕 订单原币币种快照（Phase 3 - F5） */
    private String currency;
    /** 🆕 下单时锁定汇率：1 外币 = X 人民币 */
    private BigDecimal exchangeRate;
    /** 🆕 原币金额快照 */
    private BigDecimal originalAmount;
    /** 🆕 订单来源: NORMAL / LIVE_FLASH / GROUP_BUY（Phase 4 - F7） */
    private String orderSource;
    /** 🆕 拼团团实例ID（Phase 4 - F7） */
    private Long groupRecordId;
    /** 🆕 本单消耗积分（Phase 4 - F8） */
    private Integer pointsUsed;
    /** 🆕 积分抵扣金额（Phase 4 - F8） */
    private BigDecimal pointsDeduction;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String paymentType;
    private Long paymentId;
    private LocalDateTime payTime;
    private String carrierName;
    private String trackingNumber;
    private LocalDateTime shippedAt;
    private LocalDateTime createTime;
    private List<TradeOrderItem> items;
}
