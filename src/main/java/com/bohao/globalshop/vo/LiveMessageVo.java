package com.bohao.globalshop.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveMessageVo {
    private Long id;
    private Long userId;
    private String nickname;
    private String content;
    private Integer type; // 0-普通弹幕 1-提问 2-AI回复 3-系统消息
    private LocalDateTime createTime;
}
