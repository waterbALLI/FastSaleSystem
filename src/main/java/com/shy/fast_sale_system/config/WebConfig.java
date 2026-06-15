package com.shy.fast_sale_system.config;

import com.shy.fast_sale_system.interceptor.AuthInterceptor;
import com.shy.fast_sale_system.interceptor.MetricsInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private MetricsInterceptor metricsInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 监控拦截器（记录所有请求指标，不拦截请求）
        registry.addInterceptor(metricsInterceptor)
                .addPathPatterns("/**");

        // 认证拦截器
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login", "/user/register", "/static/**",
                        "/seckill_detail.html", "/admin.html", "/admin/login", "/health");
    }
}
