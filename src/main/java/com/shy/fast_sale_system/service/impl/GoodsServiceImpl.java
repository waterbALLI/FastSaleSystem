package com.shy.fast_sale_system.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GoodsServiceImpl implements GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
//用于操作分布式的缓存，那什么是分布式的缓存呢？
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
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getGoodsId, goodsId));
        vo.setSeckillPrice(sg != null ? sg.getSeckillPrice() : goods.getGoodsPrice());

        return vo;
    }

    @Override
    public List<GoodsVo> getSeckillList() {
        List<SeckillGoods> sgList = seckillGoodsMapper.selectList(null);
        List<GoodsVo> result = new ArrayList<>();

        for (SeckillGoods sg : sgList) {
            Goods goods = getGoodsById(sg.getGoodsId());
            if (goods == null) continue;

            GoodsVo vo = new GoodsVo();
            vo.setId(goods.getId());
            vo.setGoodsName(goods.getGoodsName());
            vo.setGoodsPrice(goods.getGoodsPrice());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setSeckillStock(sg.getSeckillStock());

            // Redis 实时库存（兼容多种序列化格式）
            vo.setStockCount(readStockFromRedis("goods:stock:" + sg.getGoodsId(), sg.getSeckillStock()));

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
}