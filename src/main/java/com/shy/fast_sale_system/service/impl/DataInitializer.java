package com.shy.fast_sale_system.service.impl;

import com.shy.fast_sale_system.controller.OrderController;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderController orderController;

    @Override
    public void run(String... args) throws Exception {
        // 项目启动时：从秒杀商品表读取库存，预热到 Redis
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);
        for (SeckillGoods sg : seckillGoodsList) {
            String redisKey = "goods:stock:" + sg.getGoodsId();
            // 使用 setIfAbsent：只在 key 不存在时才写入，避免重启覆盖已扣减的库存
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(redisKey, sg.getSeckillStock());
            if (Boolean.TRUE.equals(absent)) {
                System.out.println(">>> [秒杀预热] " + redisKey + " = " + sg.getSeckillStock() + " (新写入)");
            } else {
                System.out.println(">>> [秒杀预热] " + redisKey + " 已存在，跳过 (当前值: " + redisTemplate.opsForValue().get(redisKey) + ")");
            }
            // 初始化本地内存售罄标记：只有当 Redis 中还有库存时才标记为未售完
            Object stockObj = redisTemplate.opsForValue().get(redisKey);
            int currentStock = stockObj instanceof Integer ? (Integer) stockObj : 0;
            orderController.initSoldOutMarker(sg.getGoodsId(), currentStock <= 0);
        }
        System.out.println(">>> [秒杀系统] Redis 库存预热 + 本地内存标记初始化完成！共处理 " + seckillGoodsList.size() + " 个秒杀商品");
    }
}