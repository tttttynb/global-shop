package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserAddressDto;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserProfileUpdateDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.entity.UserAddress;
import com.bohao.globalshop.service.UserService;
import com.bohao.globalshop.vo.UserProfileVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDto dto) {
        return userService.register(dto);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDto dto) {
        return userService.login(dto);
    }

    @GetMapping("/profile")
    public Result<UserProfileVo> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.getProfile(userId);
    }

    @PutMapping("/profile")
    public Result<String> updateProfile(HttpServletRequest request, @RequestBody UserProfileUpdateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.updateProfile(userId, dto);
    }

    @GetMapping("/address/list")
    public Result<List<UserAddress>> getAddressList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.getAddressList(userId);
    }

    @PostMapping("/address")
    public Result<UserAddress> addAddress(HttpServletRequest request, @RequestBody UserAddressDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.addAddress(userId, dto);
    }

    @PutMapping("/address/{id}")
    public Result<String> updateAddress(HttpServletRequest request, @PathVariable("id") Long addressId, @RequestBody UserAddressDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.updateAddress(userId, addressId, dto);
    }

    @DeleteMapping("/address/{id}")
    public Result<String> deleteAddress(HttpServletRequest request, @PathVariable("id") Long addressId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return userService.deleteAddress(userId, addressId);
    }
}
