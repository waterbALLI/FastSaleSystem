package com.shy.fast_sale_system.common;

/**
 * 线程级用户上下文，由拦截器注入，控制器直接从该类获取当前登录用户 ID
 */
public class AuthContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static void clear() { USER_ID.remove(); }
}
