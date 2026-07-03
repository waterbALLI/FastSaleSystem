package com.shy.fast_sale_system.service;

import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.vo.OrderVo;

import java.util.List;

public interface UserService {
    /** 注册 */
    User register(String nickname, String phone, String rawPassword);

    /** 登录，成功返回用户对象，失败返回 null */
    User login(String phone, String rawPassword);

    /** 根据 ID 查用户 */
    User getById(Long userId);

    /** 查用户秒杀订单（含用户名、商品名、活动名） */
    List<OrderVo> getOrders(Long userId);
}
