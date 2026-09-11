package com.bohao.globalshop.dto;

import lombok.Data;

/**
 * 提醒订阅状态 DTO（Tier 2.2）
 */
@Data
public class AlertStatusDto {
    private boolean priceAlert;
    private boolean restockAlert;
}
