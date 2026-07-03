package com.shy.fast_sale_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.mapper.UserMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.service.UserService;
import com.shy.fast_sale_system.vo.OrderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ActivityMapper activityMapper;

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
    public List<OrderVo> getOrders(Long userId) {
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getUserId, userId)
               .orderByDesc(SeckillOrder::getCreateTime);
        List<SeckillOrder> orders = seckillOrderMapper.selectList(wrapper);
        return toOrderVoList(orders);
    }

    /** 批量转换 SeckillOrder → OrderVo（含用户名、商品名、活动名） */
    private List<OrderVo> toOrderVoList(List<SeckillOrder> orders) {
        if (orders.isEmpty()) return List.of();

        // 批量查用户
        List<Long> userIds = orders.stream().map(SeckillOrder::getUserId).distinct().toList();
        Map<Long, String> userNames = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        // 批量查商品
        List<Long> goodsIds = orders.stream().map(SeckillOrder::getGoodsId).distinct().toList();
        Map<Long, String> goodsNames = goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, Goods::getGoodsName, (a, b) -> a));

        // 批量查活动
        List<Long> activityIds = orders.stream().map(SeckillOrder::getActivityId).distinct().toList();
        Map<Long, String> activityNames = activityMapper.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, Activity::getActivityName, (a, b) -> a));

        List<OrderVo> vos = new ArrayList<>();
        for (SeckillOrder o : orders) {
            OrderVo vo = new OrderVo();
            vo.setId(o.getId());
            vo.setUserId(o.getUserId());
            vo.setGoodsId(o.getGoodsId());
            vo.setActivityId(o.getActivityId());
            vo.setOrderStatus(o.getOrderStatus());
            vo.setSeckillPrice(o.getSeckillPrice());
            vo.setCreateTime(o.getCreateTime());
            vo.setPayTime(o.getPayTime());
            vo.setUserName(userNames.getOrDefault(o.getUserId(), "用户" + o.getUserId()));
            vo.setGoodsName(goodsNames.getOrDefault(o.getGoodsId(), "商品" + o.getGoodsId()));
            vo.setActivityName(activityNames.getOrDefault(o.getActivityId(), "活动" + o.getActivityId()));
            vos.add(vo);
        }
        return vos;
    }
}
