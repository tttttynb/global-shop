package com.bohao.globalshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user")// 告诉 MyBatis-Plus 这个类对应数据库的 user 表
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private BigDecimal balance;
    private String nickname;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
