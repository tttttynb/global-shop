package com.bohao.globalshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bohao.globalshop.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消费画像 Mapper
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
