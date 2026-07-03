package com.shy.fast_sale_system.service.impl;

import com.shy.fast_sale_system.controller.OrderController;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderController orderController;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("========== [秒杀系统] 启动预热开始 ==========");

        // 1. 预热活动信息到 Redis
        List<Activity> activities = activityMapper.selectList(null);
        for (Activity a : activities) {
            activityService.cacheActivityInfo(a);
        }
        System.out.println(">>> [启动预热] 活动信息缓存完成，共 " + activities.size() + " 个活动");

        // 2. 预热秒杀商品库存、商品信息
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);
        for (SeckillGoods sg : seckillGoodsList) {
            // 2a. 库存预热
            String stockKey = "goods:stock:" + sg.getGoodsId();
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(stockKey, sg.getSeckillStock());
            if (Boolean.TRUE.equals(absent)) {
                System.out.println(">>> [启动预热] " + stockKey + " = " + sg.getSeckillStock() + " (新写入)");
            } else {
                System.out.println(">>> [启动预热] " + stockKey + " 已存在，跳过 (当前值: " + redisTemplate.opsForValue().get(stockKey) + ")");
            }

            // 2b. 商品信息预热
            Goods goods = goodsMapper.selectById(sg.getGoodsId());
            if (goods != null) {
                String infoKey = "goods:info:" + sg.getGoodsId();
                redisTemplate.opsForValue().setIfAbsent(infoKey, goods, 1, TimeUnit.HOURS);
            }

            // 2c. 初始化本地内存售罄标记
            Object stockObj = redisTemplate.opsForValue().get(stockKey);
            int currentStock = stockObj instanceof Integer ? (Integer) stockObj : 0;
            orderController.initSoldOutMarker(sg.getGoodsId(), currentStock <= 0);

            // 2d. 预热用户集合过期时间（绑定活动）
            String userSetKey = "seckill:users:" + sg.getActivityId() + ":" + sg.getGoodsId();
            redisTemplate.expire(userSetKey, 24, TimeUnit.HOURS);
        }
        System.out.println("========== [秒杀系统] 启动预热完成！共处理 " + seckillGoodsList.size() + " 个秒杀商品 ==========");
    }
}