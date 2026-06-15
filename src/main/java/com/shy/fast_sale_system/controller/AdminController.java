package com.shy.fast_sale_system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import com.shy.fast_sale_system.service.PreHeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 REST API
 * 认证方式：Header 中携带 admin token
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Value("${admin.token:admin-secret-token}")
    private String adminToken;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private PreHeatService preHeatService;

    // ==================== 权限校验 ====================

    private boolean checkAdmin(String authHeader) {
        if (authHeader == null) return false;
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;
        return adminToken.equals(token);
    }

    private <T> Result<T> unauth() {
        return Result.error("管理员权限不足");
    }

    // ==================== 管理员登录 ====================

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (adminToken.equals(token)) {
            Map<String, String> data = new HashMap<>();
            data.put("adminToken", token);
            log.info("[管理员登录] 成功");
            return Result.success(data);
        }
        return Result.error("管理员 Token 错误");
    }

    // ==================== 商品管理 ====================

    @GetMapping("/goods")
    public Result<List<Goods>> listGoods(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!checkAdmin(auth)) return unauth();
        List<Goods> list = goodsMapper.selectList(
                new LambdaQueryWrapper<Goods>().eq(Goods::getIsDeleted, 0));
        return Result.success(list);
    }

    @PostMapping("/goods")
    public Result<Goods> createGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @RequestBody Goods goods) {
        if (!checkAdmin(auth)) return unauth();
        goodsMapper.insert(goods);
        log.info("[管理员] 创建商品 id={}, name={}", goods.getId(), goods.getGoodsName());
        return Result.success(goods);
    }

    @PutMapping("/goods/{id}")
    public Result<String> updateGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long id, @RequestBody Goods goods) {
        if (!checkAdmin(auth)) return unauth();
        goods.setId(id);
        goodsMapper.updateById(goods);
        log.info("[管理员] 更新商品 id={}", id);
        return Result.success("更新成功");
    }

    @DeleteMapping("/goods/{id}")
    public Result<String> deleteGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        // 逻辑删除
        Goods goods = new Goods();
        goods.setId(id);
        goods.setIsDeleted(1);
        goodsMapper.updateById(goods);
        log.info("[管理员] 删除商品 id={}", id);
        return Result.success("删除成功");
    }

    // ==================== 活动管理 ====================

    @GetMapping("/activities")
    public Result<List<Activity>> listActivities(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!checkAdmin(auth)) return unauth();
        List<Activity> list = activityMapper.selectList(null);
        return Result.success(list);
    }

    @PostMapping("/activities")
    public Result<Activity> createActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestBody Activity activity) {
        if (!checkAdmin(auth)) return unauth();
        activityMapper.insert(activity);
        log.info("[管理员] 创建活动 id={}, name={}", activity.getId(), activity.getActivityName());
        return Result.success(activity);
    }

    @PutMapping("/activities/{id}")
    public Result<String> updateActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id, @RequestBody Activity activity) {
        if (!checkAdmin(auth)) return unauth();
        activity.setId(id);
        activityMapper.updateById(activity);
        log.info("[管理员] 更新活动 id={}", id);
        return Result.success("更新成功");
    }

    @DeleteMapping("/activities/{id}")
    public Result<String> deleteActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        activityMapper.deleteById(id);
        log.info("[管理员] 删除活动 id={}", id);
        return Result.success("删除成功");
    }

    // ==================== 秒杀商品管理 ====================

    @GetMapping("/seckill-goods")
    public Result<List<SeckillGoods>> listSeckillGoods(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!checkAdmin(auth)) return unauth();
        List<SeckillGoods> list = seckillGoodsMapper.selectList(null);
        return Result.success(list);
    }

    @PostMapping("/seckill-goods")
    public Result<SeckillGoods> createSeckillGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestBody SeckillGoods sg) {
        if (!checkAdmin(auth)) return unauth();
        seckillGoodsMapper.insert(sg);
        log.info("[管理员] 创建秒杀商品 id={}, goodsId={}, activityId={}", sg.getId(), sg.getGoodsId(), sg.getActivityId());
        return Result.success(sg);
    }

    @PutMapping("/seckill-goods/{id}")
    public Result<String> updateSeckillGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable Long id, @RequestBody SeckillGoods sg) {
        if (!checkAdmin(auth)) return unauth();
        sg.setId(id);
        seckillGoodsMapper.updateById(sg);
        log.info("[管理员] 更新秒杀商品 id={}", id);
        return Result.success("更新成功");
    }

    @DeleteMapping("/seckill-goods/{id}")
    public Result<String> deleteSeckillGoods(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        seckillGoodsMapper.deleteById(id);
        log.info("[管理员] 删除秒杀商品 id={}", id);
        return Result.success("删除成功");
    }

    // ==================== 订单管理 ====================

    @GetMapping("/orders")
    public Result<Map<String, Object>> listOrders(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!checkAdmin(auth)) return unauth();
        Page<SeckillOrder> p = new Page<>(page, size);
        p = seckillOrderMapper.selectPage(p,
                new LambdaQueryWrapper<SeckillOrder>().orderByDesc(SeckillOrder::getCreateTime));
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        return Result.success(data);
    }

    // ==================== 手动预热 ====================

    @PostMapping("/preheat")
    public Result<String> preHeat(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!checkAdmin(auth)) return unauth();
        try {
            preHeatService.preHeat();
            return Result.success("预热完成");
        } catch (Exception e) {
            log.error("[管理员] 预热失败", e);
            return Result.error("预热失败: " + e.getMessage());
        }
    }
}
