package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.common.CircuitBreaker;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 */
@Slf4j
@RestController
public class HealthController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();

        // Redis
        try {
            redisTemplate.opsForValue().get("health:check");
            status.put("redis", "UP");
            circuitBreaker.recordSuccess("redis");
        } catch (Exception e) {
            status.put("redis", "DOWN");
            circuitBreaker.recordFailure("redis");
            log.error("[健康检查] Redis 不可用", e);
        }

        // RabbitMQ
        try {
            if (rabbitTemplate != null) {
                // 简单检查：尝试声明一个临时队列
                rabbitTemplate.execute(channel -> {
                    channel.queueDeclarePassive("seckill.queue");
                    return null;
                });
                status.put("rabbitmq", "UP");
                circuitBreaker.recordSuccess("rabbitmq");
            } else {
                status.put("rabbitmq", "NOT_CONFIGURED");
            }
        } catch (Exception e) {
            status.put("rabbitmq", "DOWN");
            circuitBreaker.recordFailure("rabbitmq");
            log.error("[健康检查] RabbitMQ 不可用", e);
        }

        // MySQL
        try {
            goodsMapper.selectCount(null);
            status.put("database", "UP");
            circuitBreaker.recordSuccess("mysql");
        } catch (Exception e) {
            status.put("database", "DOWN");
            circuitBreaker.recordFailure("mysql");
            log.error("[健康检查] MySQL 不可用", e);
        }

        // 熔断状态
        Map<String, String> cbStatus = new HashMap<>();
        circuitBreaker.getAllStates().forEach((k, v) ->
                cbStatus.put(k, v.isOpen() ? "OPEN" : "CLOSED"));
        status.put("circuitBreakers", cbStatus);

        return Result.success(status);
    }
}
