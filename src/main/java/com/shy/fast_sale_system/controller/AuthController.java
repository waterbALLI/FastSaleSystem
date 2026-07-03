package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.common.AuthContext;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.vo.OrderVo;
import com.shy.fast_sale_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 注册 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        String phone = body.get("phone");
        String password = body.get("password");
        if (nickname == null || nickname.isBlank() || phone == null || phone.isBlank()
                || password == null || password.isBlank()) {
            return Result.error("昵称、手机号、密码均不能为空");
        }
        User user = userService.register(nickname.trim(), phone.trim(), password);
        if (user == null) {
            return Result.error("手机号已注册");
        }
        String token = generateToken(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.success(data);
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return Result.error("手机号和密码不能为空");
        }
        User user = userService.login(phone.trim(), password);
        if (user == null) {
            return Result.error("手机号或密码错误");
        }
        String token = generateToken(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.success(data);
    }

    /** 获取当前用户信息 */
    @GetMapping("/info")
    public Result<User> getInfo() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userService.getById(userId);
        return user != null ? Result.success(user) : Result.error("用户不存在");
    }

    /** 登出 */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && !token.isEmpty()) {
            redisTemplate.delete("token:" + token);
        }
        return Result.success("已登出");
    }

    /** 当前用户秒杀订单 */
    @GetMapping("/orders")
    public Result<List<OrderVo>> getOrders() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        return Result.success(userService.getOrders(userId));
    }

    private String generateToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("token:" + token, userId, 30, TimeUnit.MINUTES);
        return token;
    }
}
