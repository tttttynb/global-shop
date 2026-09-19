package com.bohao.globalshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bohao.globalshop.entity.MemberPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberPointsMapper extends BaseMapper<MemberPoints> {

    /**
     * 原子扣减积分（防并发透支）：仅当 points >= amount 时扣减成功
     */
    @Update("UPDATE member_points SET points = points - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND points >= #{amount}")
    int deductPoints(@Param("userId") Long userId, @Param("amount") int amount);
}
