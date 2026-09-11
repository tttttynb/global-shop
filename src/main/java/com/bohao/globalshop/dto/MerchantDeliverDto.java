package com.bohao.globalshop.dto;

import lombok.Data;

/**
 * 商家发货请求 DTO
 */
@Data
public class MerchantDeliverDto {

    /** 订单ID */
    private Long orderId;

    /** 物流公司名称（必填，如"顺丰速运"） */
    private String carrierName;

    /** 物流公司编码（选填，如"SF"，对接第三方API时使用） */
    private String carrierCode;

    /** 运单号（必填） */
    private String trackingNumber;
}
