package com.shy.fast_sale_system.interceptor;

import com.shy.fast_sale_system.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Header 或 Cookie 中取 token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            // 也尝试从 URL 参数或 Cookie 取
            token = request.getParameter("token");
        }
        if (token != null && !token.isEmpty()) {
            String key = "token:" + token;
            Object userIdObj = redisTemplate.opsForValue().get(key);
            if (userIdObj != null) {
                Long userId = userIdObj instanceof Integer
                        ? ((Integer) userIdObj).longValue()
                        : (Long) userIdObj;
                AuthContext.setUserId(userId);
                // 刷新 token 过期时间（续期）
                redisTemplate.expire(key, 30, java.util.concurrent.TimeUnit.MINUTES);
            }
        }
        // 即使没有 token 也放行，控制器自己判断是否需要登录
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
