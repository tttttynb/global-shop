package com.bohao.globalshop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundApplyDto {
    private Long orderItemId;
    private BigDecimal refundAmount;
    private String reason;
    private String images;
}
