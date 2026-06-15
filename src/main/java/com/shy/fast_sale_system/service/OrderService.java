package com.shy.fast_sale_system.service;

import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.vo.GoodsVo;

public interface OrderService {
    /**
     * 执行秒杀下单（校验库存 + 创建订单）
     * @return 订单ID（>0 成功）；-1 失败；0 排队中
     */
    long seckill(User user, GoodsVo goods);
}
