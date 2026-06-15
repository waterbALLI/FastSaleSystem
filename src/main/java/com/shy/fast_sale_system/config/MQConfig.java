package com.shy.fast_sale_system.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MQConfig {

    public static final String SECKILL_QUEUE = "seckill.queue";

    @Bean
    public Queue queue() {
        // 参数说明：队列名称，是否持久化（重启后队列是否还在）
        return new Queue(SECKILL_QUEUE, true);
    }
}
