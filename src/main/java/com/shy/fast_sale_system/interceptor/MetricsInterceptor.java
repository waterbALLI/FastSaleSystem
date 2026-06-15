package com.shy.fast_sale_system.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控指标拦截器：记录每个接口的请求数、成功/失败数、总延迟
 */
@Component
public class MetricsInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, EndpointMetrics> metricsMap = new ConcurrentHashMap<>();

    /** 记录时间窗口起始（用于 QPS 计算） */
    private volatile long windowStart = System.currentTimeMillis();
    private final AtomicLong windowRequestCount = new AtomicLong(0);

    @Data
    public static class EndpointMetrics {
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("_metrics_start", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long start = (Long) request.getAttribute("_metrics_start");
        if (start == null) return;

        long latency = System.currentTimeMillis() - start;
        String path = normalizePath(request.getRequestURI());

        EndpointMetrics m = metricsMap.computeIfAbsent(path, k -> new EndpointMetrics());
        m.totalRequests.incrementAndGet();
        m.totalLatencyMs.addAndGet(latency);

        if (ex != null || response.getStatus() >= 400) {
            m.failureCount.incrementAndGet();
        } else {
            m.successCount.incrementAndGet();
        }

        // 窗口计数
        windowRequestCount.incrementAndGet();

        // 每秒重置窗口
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            synchronized (this) {
                if (now - windowStart >= 1000) {
                    windowStart = now;
                    windowRequestCount.set(0);
                }
            }
        }
    }

    /** 把带参数/动态路径归一化 */
    private String normalizePath(String uri) {
        // /order/uuid-path/do_seckill → /order/{path}/do_seckill
        uri = uri.replaceAll("/order/[a-f0-9\\-]{30,}/do_seckill", "/order/{path}/do_seckill");
        // /admin/goods/123 → /admin/goods/{id}
        uri = uri.replaceAll("/admin/goods/\\d+$", "/admin/goods/{id}");
        uri = uri.replaceAll("/admin/activities/\\d+$", "/admin/activities/{id}");
        uri = uri.replaceAll("/admin/seckill-goods/\\d+$", "/admin/seckill-goods/{id}");
        return uri;
    }

    /** 获取所有指标快照 */
    public ConcurrentHashMap<String, EndpointMetrics> getMetrics() {
        return metricsMap;
    }

    /** 获取当前 QPS（基于滑动窗口） */
    public long getCurrentQps() {
        return windowRequestCount.get();
    }
}
