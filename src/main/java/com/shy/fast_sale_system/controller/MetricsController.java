package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.interceptor.MetricsInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控指标接口（管理后台调用）
 */
@RestController
@RequestMapping("/admin")
public class MetricsController {

    @Autowired
    private MetricsInterceptor metricsInterceptor;

    @GetMapping("/metrics")
    public Result<Map<String, Object>> getMetrics(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        // 简单的 token 校验（复用 admin 逻辑）
        // 由 AdminController 处理认证，这里仅返回数据

        Map<String, Object> result = new HashMap<>();
        long totalRequests = 0;
        long totalSuccess = 0;
        long totalFailure = 0;
        long totalLatency = 0;
        long endpointCount = 0;

        for (MetricsInterceptor.EndpointMetrics m : metricsInterceptor.getMetrics().values()) {
            totalRequests += m.getTotalRequests().get();
            totalSuccess += m.getSuccessCount().get();
            totalFailure += m.getFailureCount().get();
            totalLatency += m.getTotalLatencyMs().get();
            endpointCount++;
        }

        result.put("totalRequests", totalRequests);
        result.put("qps", metricsInterceptor.getCurrentQps());
        result.put("successCount", totalSuccess);
        result.put("failureCount", totalFailure);
        result.put("successRate", totalRequests > 0
                ? Math.round((double) totalSuccess / totalRequests * 10000) / 100.0
                : 100.0);
        result.put("avgLatency", totalRequests > 0
                ? Math.round((double) totalLatency / totalRequests)
                : 0);
        result.put("endpointCount", endpointCount);

        // 各接口明细
        Map<String, Map<String, Object>> details = new HashMap<>();
        metricsInterceptor.getMetrics().forEach((path, m) -> {
            Map<String, Object> detail = new HashMap<>();
            long req = m.getTotalRequests().get();
            detail.put("requests", req);
            detail.put("success", m.getSuccessCount().get());
            detail.put("failure", m.getFailureCount().get());
            detail.put("avgLatency", req > 0
                    ? Math.round((double) m.getTotalLatencyMs().get() / req)
                    : 0);
            details.put(path, detail);
        });
        result.put("endpoints", details);

        return Result.success(result);
    }
}
