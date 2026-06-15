package com.shy.fast_sale_system.common;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单熔断器：基于失败计数的熔断/半开/闭合状态机
 *
 * 使用方式：
 *   if (!circuitBreaker.isAvailable("rabbitmq")) { // 降级处理 }
 *   try { ... circuitBreaker.recordSuccess("rabbitmq"); }
 *   catch (Exception e) { circuitBreaker.recordFailure("rabbitmq"); }
 */
@Slf4j
@Component
public class CircuitBreaker {

    /** 默认：连续 10 次失败触发熔断 */
    private static final int DEFAULT_THRESHOLD = 10;
    /** 默认：熔断后 30 秒进入半开状态 */
    private static final long DEFAULT_COOLDOWN_MS = 30_000;

    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    @Data
    public static class CircuitState {
        private int failureCount;
        private long lastFailureTime;
        private volatile boolean open;
        private final int threshold;
        private final long cooldownMs;

        public CircuitState(int threshold, long cooldownMs) {
            this.threshold = threshold;
            this.cooldownMs = cooldownMs;
        }
    }

    private CircuitState getOrCreate(String downstream) {
        return states.computeIfAbsent(downstream,
                k -> new CircuitState(DEFAULT_THRESHOLD, DEFAULT_COOLDOWN_MS));
    }

    /**
     * 检查下游服务是否可用
     */
    public boolean isAvailable(String downstream) {
        CircuitState state = states.get(downstream);
        if (state == null) return true;

        if (state.open) {
            long elapsed = System.currentTimeMillis() - state.lastFailureTime;
            if (elapsed > state.cooldownMs) {
                // 冷却期过，进入半开状态（允许一次尝试）
                state.open = false;
                state.failureCount = 0;
                log.info("[熔断器] {} 进入半开状态，允许试探请求", downstream);
                return true;
            }
            log.warn("[熔断器] {} 已熔断，拒绝请求 (冷却剩余 {}ms)", downstream,
                    state.cooldownMs - elapsed);
            return false;
        }
        return true;
    }

    /**
     * 记录一次成功
     */
    public void recordSuccess(String downstream) {
        CircuitState state = states.get(downstream);
        if (state != null) {
            state.failureCount = 0;
            state.open = false;
        }
    }

    /**
     * 记录一次失败，连续失败超过阈值则熔断
     */
    public void recordFailure(String downstream) {
        CircuitState state = getOrCreate(downstream);
        state.failureCount++;
        state.lastFailureTime = System.currentTimeMillis();

        if (state.failureCount >= state.threshold && !state.open) {
            state.open = true;
            log.error("[熔断器] {} 触发熔断！连续失败 {} 次", downstream, state.failureCount);
        }
    }

    /**
     * 手动重置下游状态
     */
    public void reset(String downstream) {
        states.remove(downstream);
        log.info("[熔断器] {} 已手动重置", downstream);
    }

    /**
     * 获取当前所有状态（供监控使用）
     */
    public ConcurrentHashMap<String, CircuitState> getAllStates() {
        return states;
    }
}
