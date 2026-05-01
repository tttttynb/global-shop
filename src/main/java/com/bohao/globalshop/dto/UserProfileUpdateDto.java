package com.bohao.globalshop.dto;

import lombok.Data;

@Data
public class UserProfileUpdateDto {
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
}
