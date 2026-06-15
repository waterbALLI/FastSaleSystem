package com.shy.fast_sale_system.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shy.fast_sale_system.config.MQConfig;
import com.shy.fast_sale_system.controller.OrderController;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.service.OrderService;
import com.shy.fast_sale_system.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MQReceiver {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderController orderController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = MQConfig.SECKILL_QUEUE)
    public void receive(String message) {
        log.info("【MQ 消费者】收到秒杀下单消息：{}", message);

        SeckillMessage msg = null;
        Long goodsId = null;
        Long userId = null;
        String resultKey = null;

        try {
            // 1. 反序列化消息
            msg = objectMapper.readValue(message, SeckillMessage.class);
            User user = msg.getUser();
            goodsId = msg.getGoodsId();
            userId = user.getId();
            resultKey = "seckill:result:" + userId + ":" + goodsId;

            // 2. 获取商品信息（仅校验商品存在，不做库存二次检查）
            GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
            if (goods == null) {
                log.error("【MQ 消费者】商品不存在 goodsId={}", goodsId);
                goodsService.rollbackStock(goodsId);
                orderController.clearSoldOutMarker(goodsId);
                redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                return;
            }

            // 3. 执行秒杀下单（含 Redisson 分布式锁 + MySQL 库存扣减 + 写订单）
            long orderId = orderService.seckill(user, goods);
            if (orderId <= 0) {
                // 下单失败 → 回补 Redis 库存
                log.warn("【MQ 消费者】下单失败 (return={})，执行库存回补 goodsId={}", orderId, goodsId);
                goodsService.rollbackStock(goodsId);
                orderController.clearSoldOutMarker(goodsId);
            }

        } catch (Exception e) {
            log.error("【MQ 消费者】处理秒杀消息失败：{}", message, e);
            // 异常时回补库存 + 通知前端
            if (goodsId != null) {
                try {
                    goodsService.rollbackStock(goodsId);
                    orderController.clearSoldOutMarker(goodsId);
                } catch (Exception ex) {
                    log.error("【MQ 消费者】库存回补失败 goodsId={}", goodsId, ex);
                }
            }
            if (resultKey != null) {
                try {
                    redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.error("【MQ 消费者】写入失败结果到 Redis 也失败了", ex);
                }
            }
        }
    }
}
