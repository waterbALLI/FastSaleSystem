package com.shy.fast_sale_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.service.ActivityService;
import com.shy.fast_sale_system.service.PreHeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 手动预热服务：将 MySQL 中的数据重新加载到 Redis
 */
@Service
@Slf4j
public class PreHeatServiceImpl implements PreHeatService {

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

    @Override
    public void preHeat() {
        log.info("========== [手动预热] 开始 ==========");

        // 1. 预热活动信息
        List<Activity> activities = activityMapper.selectList(null);
        for (Activity activity : activities) {
            activityService.cacheActivityInfo(activity);
        }
        log.info("[手动预热] 活动信息缓存完成，共 {} 个活动", activities.size());

        // 2. 预热商品和库存
        List<SeckillGoods> sgList = seckillGoodsMapper.selectList(null);
        for (SeckillGoods sg : sgList) {
            // 2a. 库存预热（仅当 key 不存在时才写入，保留已扣减库存）
            String stockKey = "goods:stock:" + sg.getGoodsId();
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(stockKey, sg.getSeckillStock());
            if (Boolean.TRUE.equals(absent)) {
                log.info("[手动预热] {} = {} (新写入)", stockKey, sg.getSeckillStock());
            } else {
                Object current = redisTemplate.opsForValue().get(stockKey);
                log.info("[手动预热] {} 已存在，当前值={}", stockKey, current);
            }

            // 2b. 商品信息预热
            Goods goods = goodsMapper.selectById(sg.getGoodsId());
            if (goods != null) {
                String infoKey = "goods:info:" + sg.getGoodsId();
                redisTemplate.opsForValue().set(infoKey, goods, 1, TimeUnit.HOURS);
            }

            // 2c. 预热用户集合（空 Set）
            String userSetKey = "seckill:users:" + sg.getGoodsId();
            // 设置过期时间，防止永久占用内存
            redisTemplate.expire(userSetKey, 24, TimeUnit.HOURS);
        }
        log.info("[手动预热] 商品/库存/用户集合缓存完成，共 {} 个秒杀商品", sgList.size());

        log.info("========== [手动预热] 完成 ==========");
    }
}
