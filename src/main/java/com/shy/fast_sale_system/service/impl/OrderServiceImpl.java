package com.shy.fast_sale_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.service.OrderService;
import com.shy.fast_sale_system.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public long seckill(User user, GoodsVo goods) {
        Long userId = user.getId();
        Long goodsId = goods.getId();

        String lockKey = "lock:seckill:" + userId + ":" + goodsId;
        String resultKey = "seckill:result:" + userId + ":" + goodsId;

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // ==================== 分布式锁：防重复下单的最后一道铁闸 ====================
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                log.warn("[分布式锁] 获取锁失败，疑似并发冲突 userId={}, goodsId={}", userId, goodsId);
                redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                return -1;
            }

            // ==================== 1. MySQL 真实库存兜底检查 ====================
            Goods dbGoods = goodsMapper.selectById(goodsId);
            if (dbGoods == null || dbGoods.getGoodsStock() <= 0) {
                log.warn("[下单失败] MySQL库存不足 goodsId={}, stock={}",
                        goodsId, dbGoods != null ? dbGoods.getGoodsStock() : "null");
                redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                return -1;
            }

            // ==================== 2. 防重复下单 ====================
            LambdaQueryWrapper<SeckillOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SeckillOrder::getUserId, userId)
                       .eq(SeckillOrder::getGoodsId, goodsId)
                       .orderByDesc(SeckillOrder::getId)
                       .last("LIMIT 1");
            SeckillOrder existOrder = seckillOrderMapper.selectOne(queryWrapper);
            if (existOrder != null) {
                log.warn("[下单失败] 重复下单 userId={}, goodsId={}, 已有订单号={}", userId, goodsId, existOrder.getId());
                redisTemplate.opsForValue().set(resultKey, existOrder.getId(), 300, TimeUnit.SECONDS);
                return existOrder.getId();
            }

            // ==================== 3. 扣减 MySQL 库存（行锁保证原子性） ====================
            LambdaUpdateWrapper<Goods> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Goods::getId, goodsId)
                        .gt(Goods::getGoodsStock, 0)
                        .setSql("goods_stock = goods_stock - 1");
            int rows = goodsMapper.update(null, updateWrapper);
            if (rows <= 0) {
                log.warn("[下单失败] MySQL库存扣减失败 goodsId={}", goodsId);
                redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                return -1;
            }

            // ==================== 4. 查询秒杀价格 ====================
            LambdaQueryWrapper<SeckillGoods> sgWrapper = new LambdaQueryWrapper<>();
            sgWrapper.eq(SeckillGoods::getGoodsId, goodsId);
            SeckillGoods sg = seckillGoodsMapper.selectOne(sgWrapper);
            BigDecimal seckillPrice = sg != null
                    ? BigDecimal.valueOf(sg.getSeckillPrice())
                    : BigDecimal.ZERO;

            // ==================== 5. 创建秒杀订单 ====================
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setGoodsId(goodsId);
            order.setActivityId(sg != null ? sg.getActivityId() : 1L);
            order.setSeckillPrice(seckillPrice);
            order.setOrderStatus(0); // 待支付
            seckillOrderMapper.insert(order);

            // ==================== 6. 写成功结果到 Redis ====================
            redisTemplate.opsForValue().set(resultKey, order.getId(), 300, TimeUnit.SECONDS);
            log.info("✅ [下单成功] orderId={}, userId={}, goodsId={}, price={}",
                    order.getId(), userId, goodsId, seckillPrice);

            return order.getId();

        } catch (Exception e) {
            log.error("[下单异常] userId={}, goodsId={}", userId, goodsId, e);
            redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
            return -1;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
