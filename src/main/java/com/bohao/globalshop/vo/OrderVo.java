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
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime createTime;
    private List<TradeOrderItem> items;
}
