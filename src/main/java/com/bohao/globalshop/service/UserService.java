package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserRegisterDto;

public interface UserService {
    Result<String> register(UserRegisterDto dto);

    Result<String> login(UserLoginDto dto);
}
