package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class UserAddressDto {
    private String receiverName;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Boolean isDefault;
}
