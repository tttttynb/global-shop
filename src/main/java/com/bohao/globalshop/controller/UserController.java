package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDto dto) {
        return userService.register(dto);
    }

    @PostMapping("login")
    public Result<String> login(@RequestBody UserLoginDto dto) {
        return userService.login(dto);
    }
}
