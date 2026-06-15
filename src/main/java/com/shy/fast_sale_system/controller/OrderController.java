package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.common.AuthContext;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.rabbitmq.MQSender;
import com.shy.fast_sale_system.rabbitmq.SeckillMessage;
import com.shy.fast_sale_system.service.ActivityService;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.service.OrderService;
import com.shy.fast_sale_system.service.UserService;
import com.shy.fast_sale_system.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private UserService userService;

    @Autowired
    private MQSender mqSender;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ActivityService activityService;

    // ==================== 限流 Lua 脚本（固定窗口计数器） ====================
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    // key = 商品ID, value = true 代表已售罄
    private final Map<Long, Boolean> localOverMap = new ConcurrentHashMap<>();

    // ==================== 第一道防线：数学验证码 ====================

    @GetMapping("/captcha")
    public Result<String> getCaptcha(@RequestParam("goodsId") Long goodsId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        int a = (int) (Math.random() * 10) + 1;
        int b = (int) (Math.random() * 10) + 1;
        int c = (int) (Math.random() * 10) + 1;

        String expression = a + " + " + b + " × " + c + " = ?";
        int answer = a + b * c;

        String captchaKey = "captcha:" + userId + ":" + goodsId;
        redisTemplate.opsForValue().set(captchaKey, answer, 60, TimeUnit.SECONDS);

        log.info("[验证码生成] expression={}, answer={}", expression, answer);
        return Result.success(expression);
    }

    // ==================== 第二道防线：动态秒杀地址 ====================

    @GetMapping("/path")
    public Result<String> getSeckillPath(@RequestParam("goodsId") Long goodsId,
                                          @RequestParam("captchaAnswer") Integer captchaAnswer) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        String captchaKey = "captcha:" + userId + ":" + goodsId;
        Object savedAnswer = redisTemplate.opsForValue().get(captchaKey);
        if (savedAnswer == null) {
            return Result.error("验证码已过期，请重新获取");
        }
        int correct = savedAnswer instanceof Integer
                ? (Integer) savedAnswer
                : Integer.parseInt(savedAnswer.toString());
        if (captchaAnswer == null || captchaAnswer != correct) {
            return Result.error("验证码错误！");
        }
        redisTemplate.delete(captchaKey);

        String path = UUID.randomUUID().toString().replace("-", "");
        String pathKey = "seckill:path:" + userId + ":" + goodsId;
        redisTemplate.opsForValue().set(pathKey, path, 60, TimeUnit.SECONDS);

        log.info("[动态路径生成] userId={}, goodsId={}, path={}", userId, goodsId, path);
        return Result.success(path);
    }

    // ==================== 秒杀接口（动态路径） ====================

    @GetMapping("/{path}/do_seckill")
    public Result<Integer> doSeckill(@PathVariable("path") String path,
                                     @RequestParam("goodsId") Long goodsId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        log.info(">>> [秒杀请求] userId={}, path={}, goodsId={}", userId, path, goodsId);

        // 动态路径校验（路径与用户绑定）
        String pathKey = "seckill:path:" + userId + ":" + goodsId;
        Object savedPath = redisTemplate.opsForValue().get(pathKey);
        if (savedPath == null || !savedPath.toString().equals(path)) {
            log.warn(">>> [路径拦截] 非法或过期 path={}, goodsId={}", path, goodsId);
            return Result.<Integer>error("秒杀地址无效或已过期，请重新获取！[路径拦截]");
        }
        redisTemplate.delete(pathKey);
        log.info("[路径校验通过] path={} 已消费", path);

        // ==================== 第一道新增防线：时间窗口校验 ====================
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goodsVo == null) {
            return Result.error("商品不存在");
        }
        if (goodsVo.getActivityId() != null && !activityService.isInTimeWindow(goodsVo.getActivityId())) {
            Integer status = goodsVo.getActivityStatus();
            String hint = status != null && status == 0 ? "秒杀活动尚未开始，请耐心等待！"
                        : status != null && status == 2 ? "秒杀活动已结束！"
                        : "当前不在秒杀时间窗口内";
            log.warn(">>> [时间窗口拦截] userId={}, goodsId={}, activityId={}, status={}",
                    userId, goodsId, goodsVo.getActivityId(), status);
            return Result.<Integer>error(hint);
        }

        // ==================== 第二道新增防线：限流（固定窗口计数器） ====================
        String rateLimitKey = "rate:limit:" + userId + ":" + goodsId;
        Long rateResult = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                Collections.singletonList(rateLimitKey), 5, 1);
        if (rateResult == null || rateResult == 0) {
            log.warn(">>> [限流拦截] userId={}, goodsId={} 请求过于频繁", userId, goodsId);
            return Result.<Integer>error("请求过于频繁，请稍后重试！[限流拦截]");
        }

        // ==================== 第三道新增防线：一人一单 Redis 前置过滤 ====================
        String userSetKey = "seckill:users:" + goodsId;
        Long added = redisTemplate.opsForSet().add(userSetKey, userId);
        if (added != null && added == 0) {
            log.warn(">>> [一人一单拦截] userId={} 已抢购过 goodsId={}", userId, goodsId);
            return Result.<Integer>error("您已经参与过该商品的秒杀，每人限购一次！[防重拦截]");
        }
        // 设置过期时间 24 小时，避免 Redis 内存泄漏
        redisTemplate.expire(userSetKey, 24, TimeUnit.HOURS);

        // 本地内存售罄标记
        Boolean isOver = localOverMap.get(goodsId);
        if (isOver != null && isOver) {
            log.warn(">>> [内存拦截] goodsId={} 已售罄", goodsId);
            return Result.<Integer>error("对不起，商品已售罄！[内存拦截]");
        }

        // 构建真实用户对象
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // Redis 预扣库存
        boolean success = goodsService.reductionStock(goodsId);
        log.info(">>> [Redis扣减] goodsId={}, success={}", goodsId, success);

        if (!success) {
            localOverMap.put(goodsId, true);
            log.warn(">>> [挂牌售罄] goodsId={} 内存标记已设为true", goodsId);
            return Result.<Integer>error("对不起，商品已售罄！");
        }

        // 投递 MQ
        SeckillMessage message = new SeckillMessage(user, goodsId);
        mqSender.sendSeckillMessage(message);
        log.info(">>> [秒杀成功] userId={}, goodsId={}, 已投递MQ", userId, goodsId);

        String resultKey = "seckill:result:" + userId + ":" + goodsId;
        redisTemplate.opsForValue().set(resultKey, 0, 120, TimeUnit.SECONDS);

        return Result.<Integer>success(0);
    }

    // ==================== 结果轮询 ====================

    @GetMapping("/result")
    public Result<String> getResult(@RequestParam("goodsId") Long goodsId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        String resultKey = "seckill:result:" + userId + ":" + goodsId;
        Object result = redisTemplate.opsForValue().get(resultKey);

        if (result == null) {
            return Result.success("0");
        }

        // 转为字符串返回，避免 JavaScript 精度丢失（Snowflake ID > 2^53）
        String value = result.toString();
        log.info("[轮询结果] userId={}, goodsId={}, result={}", userId, goodsId, value);
        return Result.success(value);
    }

    // ==================== 同步测试接口（绕过 MQ，直接下单，方便定位错误） ====================

    @GetMapping("/test-seckill")
    public Result<String> testSeckill(@RequestParam("goodsId") Long goodsId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        // Redis 预扣库存
        boolean success = goodsService.reductionStock(goodsId);
        if (!success) return Result.error("Redis 库存扣减失败（库存不足或 key 不存在）");

        // 获取商品
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) return Result.error("商品不存在 goodsId=" + goodsId);

        // 获取用户
        User user = userService.getById(userId);
        if (user == null) return Result.error("用户不存在");

        // 直接同步下单（不走 MQ），内部每一步失败都会写日志
        try {
            orderService.seckill(user, goods);
        } catch (Exception e) {
            return Result.error("下单异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // 立即查询结果
        String resultKey = "seckill:result:" + userId + ":" + goodsId;
        Object result = redisTemplate.opsForValue().get(resultKey);
        if (result == null) return Result.error("下单完成但未写入结果");
        int value = result instanceof Integer ? (Integer) result : Integer.parseInt(result.toString());
        if (value > 0) return Result.success("✅ 下单成功！订单号=" + value);
        if (value == -1) return Result.error("❌ 下单失败（详见服务端日志）");
        if (value == 0) return Result.success("⏳ 排队中（不应该出现）");
        return Result.error("未知结果=" + value);
    }

    // ==================== 支付接口 ====================

    @PostMapping("/pay/{orderId}")
    public Result<String> pay(@PathVariable Long orderId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");

        try {
            String msg = orderService.payOrder(orderId, userId);
            return msg.startsWith("✅") ? Result.success(msg) : Result.error(msg);
        } catch (Exception e) {
            log.error("[支付异常] orderId={}, userId={}", orderId, userId, e);
            return Result.error("支付失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法（供 DataInitializer / rollback 调用） ====================

    public void initSoldOutMarker(Long goodsId, boolean isOver) {
        localOverMap.put(goodsId, isOver);
        log.info("[内存标记初始化] goodsId={}, isOver={}", goodsId, isOver);
    }

    /** 清除本地售罄标记（库存回补时调用） */
    public void clearSoldOutMarker(Long goodsId) {
        localOverMap.remove(goodsId);
        log.info("[内存标记清除] goodsId={} 售罄标记已移除，供回补后重试", goodsId);
    }
}
