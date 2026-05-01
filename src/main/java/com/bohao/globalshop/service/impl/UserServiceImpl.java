package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.JwtUtils;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.UserAddressDto;
import com.bohao.globalshop.dto.UserLoginDto;
import com.bohao.globalshop.dto.UserProfileUpdateDto;
import com.bohao.globalshop.dto.UserRegisterDto;
import com.bohao.globalshop.entity.User;
import com.bohao.globalshop.entity.UserAddress;
import com.bohao.globalshop.mapper.UserAddressMapper;
import com.bohao.globalshop.mapper.UserMapper;
import com.bohao.globalshop.service.UserService;
import com.bohao.globalshop.vo.UserProfileVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserAddressMapper userAddressMapper;

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

    @Override
    public Result<UserProfileVo> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在！");
        }
        UserProfileVo vo = new UserProfileVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setBalance(user.getBalance());
        return Result.success(vo);
    }

    @Override
    public Result<String> updateProfile(Long userId, UserProfileUpdateDto dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在！");
        }
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        userMapper.updateById(user);
        return Result.success("个人信息更新成功！");
    }

    @Override
    public Result<List<UserAddress>> getAddressList(Long userId) {
        QueryWrapper<UserAddress> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("is_default").orderByDesc("create_time");
        List<UserAddress> list = userAddressMapper.selectList(qw);
        return Result.success(list);
    }

    @Override
    public Result<UserAddress> addAddress(Long userId, UserAddressDto dto) {
        // 如果设为默认地址，先把其他的都取消默认
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultAddress(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setPhone(dto.getPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        userAddressMapper.insert(address);
        return Result.success(address);
    }

    @Override
    public Result<String> updateAddress(Long userId, Long addressId, UserAddressDto dto) {
        UserAddress address = userAddressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            return Result.error(403, "地址不存在或无权操作！");
        }
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultAddress(userId);
        }
        if (dto.getReceiverName() != null) address.setReceiverName(dto.getReceiverName());
        if (dto.getPhone() != null) address.setPhone(dto.getPhone());
        if (dto.getProvince() != null) address.setProvince(dto.getProvince());
        if (dto.getCity() != null) address.setCity(dto.getCity());
        if (dto.getDistrict() != null) address.setDistrict(dto.getDistrict());
        if (dto.getDetailAddress() != null) address.setDetailAddress(dto.getDetailAddress());
        if (dto.getIsDefault() != null) address.setIsDefault(dto.getIsDefault());
        userAddressMapper.updateById(address);
        return Result.success("地址更新成功！");
    }

    @Override
    public Result<String> deleteAddress(Long userId, Long addressId) {
        UserAddress address = userAddressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            return Result.error(403, "地址不存在或无权操作！");
        }
        userAddressMapper.deleteById(addressId);
        return Result.success("地址删除成功！");
    }

    private void clearDefaultAddress(Long userId) {
        QueryWrapper<UserAddress> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("is_default", true);
        List<UserAddress> defaults = userAddressMapper.selectList(qw);
        for (UserAddress addr : defaults) {
            addr.setIsDefault(false);
            userAddressMapper.updateById(addr);
        }
    }
}
