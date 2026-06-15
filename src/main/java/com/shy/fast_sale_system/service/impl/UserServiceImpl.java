package com.shy.fast_sale_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.mapper.UserMapper;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public User register(String nickname, String phone, String rawPassword) {
        // 检查手机号唯一性
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        if (userMapper.selectCount(wrapper) > 0) {
            return null; // 手机号已注册
        }
        User user = new User();
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setPassword(encoder.encode(rawPassword));
        userMapper.insert(user);
        log.info("[注册成功] userId={}, phone={}", user.getId(), phone);
        return user;
    }

    @Override
    public User login(String phone, String rawPassword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        User user = userMapper.selectOne(wrapper);
        if (user == null) return null;
        if (!encoder.matches(rawPassword, user.getPassword())) return null;
        log.info("[登录成功] userId={}, phone={}", user.getId(), phone);
        return user;
    }

    @Override
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public List<SeckillOrder> getOrders(Long userId) {
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getUserId, userId)
               .orderByDesc(SeckillOrder::getCreateTime);
        return seckillOrderMapper.selectList(wrapper);
    }
}
