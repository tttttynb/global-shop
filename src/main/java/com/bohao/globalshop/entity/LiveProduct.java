package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("live_product")
public class LiveProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long liveRoomId;
    private Long productId;
    private Integer sortOrder;
    private Boolean isExplaining; // 是否正在讲解
}
