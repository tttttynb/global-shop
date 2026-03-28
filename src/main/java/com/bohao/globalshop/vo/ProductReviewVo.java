package com.bohao.globalshop.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductReviewVo {
    private Long id;

    // 👇 专门给前端展示的脱敏买家信息
    private String username;   // 脱敏后的名字，例如：波***豪
    private String userAvatar; // 买家头像

    private Integer rating;
    private String content;
    private String images;
    private LocalDateTime createTime;
}
