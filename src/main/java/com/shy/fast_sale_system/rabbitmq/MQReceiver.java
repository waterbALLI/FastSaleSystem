package com.shy.fast_sale_system.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shy.fast_sale_system.config.MQConfig;
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
            //    注意：Redis 库存已由控制器预扣（stock.lua），这里再读 Redis 会
            //    读到扣减后的值（如 1→0），错误地把最后一单判定为"库存不足"。
            //    真正的 MySQL 库存兜底校验在 OrderServiceImpl.seckill() 内部完成。
            GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
            if (goods == null) {
                log.error("【MQ 消费者】商品不存在 goodsId={}", goodsId);
                redisTemplate.opsForValue().set(resultKey, -1, 120, TimeUnit.SECONDS);
                return;
            }

            // 3. 执行秒杀下单（含 Redisson 分布式锁 + MySQL 库存扣减 + 写订单）
            orderService.seckill(user, goods);

        } catch (Exception e) {
            log.error("【MQ 消费者】处理秒杀消息失败：{}", message, e);
            // 关键修复：异常时必须通知前端，否则轮询永远不结束
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
