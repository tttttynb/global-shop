package com.bohao.globalshop.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserProfileVo {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private BigDecimal balance;
}
