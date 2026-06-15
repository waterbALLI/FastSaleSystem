package com.shy.fast_sale_system.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shy.fast_sale_system.config.MQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将秒杀消息序列化为 JSON 并投递到 RabbitMQ 队列
     */
    public void sendSeckillMessage(SeckillMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(MQConfig.SECKILL_QUEUE, json);
            log.info("【MQ 发送者】消息已投递：{}", json);
        } catch (JsonProcessingException e) {
            log.error("【MQ 发送者】序列化失败", e);
            throw new RuntimeException("秒杀消息序列化失败", e);
        }
    }
}
