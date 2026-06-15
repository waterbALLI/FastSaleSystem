package com.shy.fast_sale_system.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shy.fast_sale_system.mapper.GoodsImageMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.GoodsReviewMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.GoodsImage;
import com.shy.fast_sale_system.pojo.GoodsReview;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.service.ActivityService;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GoodsServiceImpl implements GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private GoodsImageMapper goodsImageMapper;

    @Autowired
    private GoodsReviewMapper goodsReviewMapper;

    // 定义本地缓存：最大容量1000，写入后1分钟过期
    private Cache<Long, Goods> localCache = Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    @Override
    public Goods getGoodsById(Long goodsId) {
        // 1. 查本地缓存 (极快，无网络开销)
        Goods goods = localCache.getIfPresent(goodsId);
        if (goods != null) return goods;

        // 2. 查 Redis (分布式共享)
        String redisKey = "goods:info:" + goodsId;
        goods = (Goods) redisTemplate.opsForValue().get(redisKey);
        if (goods != null) {
            localCache.put(goodsId, goods); // 回填本地
            return goods;
        }

        // 3. 查数据库 (双检锁防止缓存击穿)
        synchronized (this) {
            // 二次检查
            goods = localCache.getIfPresent(goodsId);
            if (goods != null) return goods;

            goods = goodsMapper.selectById(goodsId);
            if (goods != null) {
                redisTemplate.opsForValue().set(redisKey, goods, 1, TimeUnit.HOURS);
                localCache.put(goodsId, goods);
            }
        }
        return goods;
    }

    // 1. 提前在类加载时解析 Lua 脚本，避免每次请求都重新读取文件
    private static final DefaultRedisScript<Long> STOCK_SCRIPT;
    static {
        STOCK_SCRIPT = new DefaultRedisScript<>();
        STOCK_SCRIPT.setLocation(new ClassPathResource("stock.lua"));
        STOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 从 Redis 安全读取库存值。
     * GenericJackson2JsonRedisSerializer 可能返回 Integer/Long/String/Number，
     * Lua decrby 之后也可能改变序列化格式，这里做多类型兼容。
     */
    private int readStockFromRedis(String stockKey, int fallback) {
        try {
            Object obj = redisTemplate.opsForValue().get(stockKey);
            if (obj == null) return fallback;
            if (obj instanceof Integer) return (Integer) obj;
            if (obj instanceof Long) return ((Long) obj).intValue();
            if (obj instanceof Number) return ((Number) obj).intValue();
            // Jackson 反序列化可能返回 String
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public GoodsVo getGoodsVoByGoodsId(Long goodsId) {
        Goods goods = getGoodsById(goodsId);
        if (goods == null) {
            return null;
        }
        GoodsVo vo = new GoodsVo();
        vo.setId(goods.getId());
        vo.setGoodsName(goods.getGoodsName());
        vo.setGoodsStock(goods.getGoodsStock());
        vo.setGoodsPrice(goods.getGoodsPrice());

        // 从 Redis 读取实时库存
        vo.setStockCount(readStockFromRedis("goods:stock:" + goodsId, goods.getGoodsStock()));

        // 查询秒杀价格
        SeckillGoods sg = seckillGoodsMapper.selectOne(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getGoodsId, goodsId));
        vo.setSeckillPrice(sg != null ? sg.getSeckillPrice() : goods.getGoodsPrice());

        // 填充商品图片
        List<GoodsImage> images = goodsImageMapper.selectList(
                new LambdaQueryWrapper<GoodsImage>()
                        .eq(GoodsImage::getGoodsId, goodsId)
                        .orderByAsc(GoodsImage::getSortOrder));
        vo.setImages(images);

        // 填充最新评论
        List<GoodsReview> reviews = goodsReviewMapper.selectList(
                new LambdaQueryWrapper<GoodsReview>()
                        .eq(GoodsReview::getGoodsId, goodsId)
                        .orderByDesc(GoodsReview::getCreateTime)
                        .last("LIMIT 20"));
        vo.setReviews(reviews);
        vo.setReviewCount(Math.toIntExact(goodsReviewMapper.selectCount(
                new LambdaQueryWrapper<GoodsReview>().eq(GoodsReview::getGoodsId, goodsId))));

        // 填充活动信息
        if (sg != null) {
            vo.setActivityId(sg.getActivityId());
            vo.setSeckillStock(sg.getSeckillStock());
            vo.setLimitPerUser(sg.getLimitPerUser());
            Activity activity = activityService.getActivityById(sg.getActivityId());
            if (activity != null) {
                vo.setActivityStartTime(activity.getStartTime());
                vo.setActivityEndTime(activity.getEndTime());
                LocalDateTime now = LocalDateTime.now();
                if (now.isBefore(activity.getStartTime())) {
                    vo.setActivityStatus(0);
                } else if (now.isBefore(activity.getEndTime())) {
                    vo.setActivityStatus(1);
                } else {
                    vo.setActivityStatus(2);
                }
            }
        }

        return vo;
    }

    @Override
    public List<GoodsVo> getSeckillList() {
        List<SeckillGoods> sgList = seckillGoodsMapper.selectList(null);
        List<GoodsVo> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (SeckillGoods sg : sgList) {
            Goods goods = getGoodsById(sg.getGoodsId());
            if (goods == null) continue;

            GoodsVo vo = new GoodsVo();
            vo.setId(goods.getId());
            vo.setGoodsName(goods.getGoodsName());
            vo.setGoodsPrice(goods.getGoodsPrice());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setSeckillStock(sg.getSeckillStock());
            vo.setActivityId(sg.getActivityId());
            vo.setLimitPerUser(sg.getLimitPerUser());

            // Redis 实时库存（兼容多种序列化格式）
            vo.setStockCount(readStockFromRedis("goods:stock:" + sg.getGoodsId(), sg.getSeckillStock()));

            // 填充活动时间信息
            Activity activity = activityService.getActivityById(sg.getActivityId());
            if (activity != null) {
                vo.setActivityStartTime(activity.getStartTime());
                vo.setActivityEndTime(activity.getEndTime());
                // 实时计算活动状态
                if (now.isBefore(activity.getStartTime())) {
                    vo.setActivityStatus(0); // 未开始
                } else if (now.isBefore(activity.getEndTime())) {
                    vo.setActivityStatus(1); // 进行中
                } else {
                    vo.setActivityStatus(2); // 已结束
                }
            }

            result.add(vo);
        }
        return result;
    }

    @Override
    public boolean reductionStock(Long goodsId) {
        String redisKey = "goods:stock:" + goodsId;

        // 执行 Lua 脚本
        // 参数1: 脚本对象; 参数2: Key列表(Collections.singletonList); 参数3: 可变参数ARGV。不要传入字符串 "1"，因为 JSON 序列化器会将其转换为带有双引号的 "\"1\""，导致 Lua tonumber 返回 nil
        Long result = redisTemplate.execute(STOCK_SCRIPT, Collections.singletonList(redisKey), 1);

        // 如果返回值 >= 0，说明 Redis 预扣库存成功
        return result != null && result >= 0;
    }

    @Override
    public void rollbackStock(Long goodsId) {
        String redisKey = "goods:stock:" + goodsId;
        Long newStock = redisTemplate.opsForValue().increment(redisKey, 1);
        log.info("[库存回补] goodsId={}, Redis stock incrby 1, 新库存={}", goodsId, newStock);
    }
}