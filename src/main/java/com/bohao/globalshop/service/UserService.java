package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserAddressDto;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserProfileUpdateDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.entity.UserAddress;
import com.bohao.globalshop.vo.UserProfileVo;

import java.math.BigDecimal;
import java.util.List;

public interface UserService {
    Result<String> register(UserRegisterDto dto);

    Result<String> login(UserLoginDto dto);

    Result<UserProfileVo> getProfile(Long userId);

    Result<String> updateProfile(Long userId, UserProfileUpdateDto dto);

    Result<List<UserAddress>> getAddressList(Long userId);

    Result<UserAddress> addAddress(Long userId, UserAddressDto dto);

    Result<String> updateAddress(Long userId, Long addressId, UserAddressDto dto);

    Result<String> deleteAddress(Long userId, Long addressId);

    /**
     * 用户余额充值
     * <p>
     * 当前为直接充值（用于开发/测试），生产环境需对接第三方支付充值流程：
     * 用户选择充值金额 → 调支付宝/微信支付 → 回调成功后调此接口
     *
     * @param userId 用户ID
     * @param amount 充值金额（必须 > 0）
     */
    Result<String> rechargeBalance(Long userId, BigDecimal amount);
}
