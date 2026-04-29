package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_room")
public class LiveRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private String title;
    private String coverImage;
    private Integer status; // 0-未开始 1-直播中 2-已结束
    private String streamKey;
    private String pushUrl;
    private String pullUrl;
    private Integer viewerCount;
    private Boolean aiAssistantEnabled; // AI助理开关
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
