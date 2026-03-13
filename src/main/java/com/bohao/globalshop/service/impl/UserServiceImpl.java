package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.JwtUtils;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public Result<String> register(UserRegisterDto dto) {
        // 1. 检查用户名是否已经被别人注册了
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        User existUser = userMapper.selectOne(queryWrapper);
        if (existUser != null) {
            return Result.error(400, "哎呀！用户名已存在，换一个试试吧！");
        }
        // 2. 如果没被注册，创建新用户并保存到数据库
        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        //使用 BCrypt 进行强加密
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(dto.getPassword());
        newUser.setPassword(encodedPassword);
        newUser.setNickname("出海打人_" + System.currentTimeMillis() % 10000);
        //插入到数据库
        userMapper.insert(newUser);
        return Result.success("太棒了，恭喜您注册成功！");
    }

    @Override
    public Result<String> login(UserLoginDto dto) {
        //1.去数据库找用户名
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        //如果用户不存在，直接拦截
        if (user == null) {
            return Result.error(401, "账号或密码错误！");
        }

        //2.比对密码
        // 注意：BCrypt 是单向加密的，我们无法把数据库里的乱码解密成 "123"。
        // 只能用 encoder.matches() 方法，把用户现在输入的明文，和数据库里的密文进行匹配算法！
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean isMatch = encoder.matches(dto.getPassword(), user.getPassword());
        if (!isMatch) {
            return Result.error(401, "账号或密码错误！");
        }
        // 3. 密码正确！给这个用户签发 JWT 通行证
        String token = JwtUtils.generateToken(user.getId(), user.getUsername());
        // 4. 把通行证发给前端
        return Result.success(token);
    }
}
