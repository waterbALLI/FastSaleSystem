package com.shy.fast_sale_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单过期处理定时任务
 * 每 30 秒扫描一次：将超过 15 分钟未支付的订单自动取消并回滚库存
 */
@Service
@Slf4j
public class OrderExpireService {

    /** 支付期限（分钟） */
    private static final int PAY_TIMEOUT_MINUTES = 15;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 30_000)
    public void cancelExpiredOrders() {
        List<SeckillOrder> expiredOrders = seckillOrderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderStatus, 0)
                        .lt(SeckillOrder::getCreateTime,
                                LocalDateTime.now().minusMinutes(PAY_TIMEOUT_MINUTES)));

        if (expiredOrders.isEmpty()) return;

        log.info("[订单过期] 发现 {} 条过期未支付订单，开始处理", expiredOrders.size());
        int cancelled = 0;

        for (SeckillOrder order : expiredOrders) {
            try {
                // 1. 更新订单状态为已取消
                LambdaUpdateWrapper<SeckillOrder> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(SeckillOrder::getId, order.getId())
                            .eq(SeckillOrder::getOrderStatus, 0)
                            .set(SeckillOrder::getOrderStatus, 2);
                int rows = seckillOrderMapper.update(null, updateWrapper);
                if (rows <= 0) continue; // 已被其他线程处理

                // 2. 回滚 MySQL 库存
                LambdaUpdateWrapper<Goods> goodsUpdate = new LambdaUpdateWrapper<>();
                goodsUpdate.eq(Goods::getId, order.getGoodsId())
                          .setSql("goods_stock = goods_stock + 1");
                goodsMapper.update(null, goodsUpdate);

                // 3. 回滚 Redis 库存
                String stockKey = "goods:stock:" + order.getGoodsId();
                redisTemplate.opsForValue().increment(stockKey, 1);

                // 4. 清除用户秒杀标记（绑定活动，允许重新购买）
                String userSetKey = "seckill:users:" + order.getActivityId() + ":" + order.getGoodsId();
                redisTemplate.opsForSet().remove(userSetKey, order.getUserId());

                // 5. 通知前端
                String resultKey = "seckill:result:" + order.getUserId() + ":" + order.getGoodsId();
                redisTemplate.opsForValue().set(resultKey, -2, 300, java.util.concurrent.TimeUnit.SECONDS);

                cancelled++;
                log.info("[订单过期] orderId={}, userId={}, goodsId={}, 已取消并回滚库存",
                        order.getId(), order.getUserId(), order.getGoodsId());

            } catch (Exception e) {
                log.error("[订单过期] 处理失败 orderId={}", order.getId(), e);
            }
        }

        log.info("[订单过期] 处理完毕，实际取消 {} 条", cancelled);
    }
}
