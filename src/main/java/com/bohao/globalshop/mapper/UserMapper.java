package com.bohao.globalshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bohao.globalshop.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承了 BaseMapper，MyBatis-Plus 已经帮你写好了几十个常用的增删改查方法！你甚至不需要写 SQL。
}
