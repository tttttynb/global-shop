package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserAddressDto;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserProfileUpdateDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.entity.UserAddress;
import com.bohao.globalshop.vo.UserProfileVo;

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
}
