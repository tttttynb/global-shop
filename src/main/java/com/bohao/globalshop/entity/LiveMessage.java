package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_message")
public class LiveMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long liveRoomId;
    private Long userId;
    private String nickname;
    private String content;
    private Integer type; // 0-普通弹幕 1-提问 2-AI回复 3-系统消息
    private LocalDateTime createTime;
}
