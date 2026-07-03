package com.shy.fast_sale_system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.mapper.GoodsImageMapper;
import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.mapper.GoodsReviewMapper;
import com.shy.fast_sale_system.mapper.SeckillGoodsMapper;
import com.shy.fast_sale_system.mapper.SeckillOrderMapper;
import com.shy.fast_sale_system.mapper.UserMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.pojo.GoodsImage;
import com.shy.fast_sale_system.pojo.GoodsReview;
import com.shy.fast_sale_system.pojo.SeckillGoods;
import com.shy.fast_sale_system.pojo.SeckillOrder;
import com.shy.fast_sale_system.pojo.User;
import com.shy.fast_sale_system.service.ActivityService;
import com.shy.fast_sale_system.service.PreHeatService;
import com.shy.fast_sale_system.vo.OrderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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

    @Autowired
    private ActivityService activityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GoodsImageMapper goodsImageMapper;

    @Autowired
    private GoodsReviewMapper goodsReviewMapper;

    @Autowired
    private UserMapper userMapper;

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
        // 动态计算活动状态
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        list.forEach(a -> {
            if (a.getStartTime() != null && now.isBefore(a.getStartTime())) a.setStatus(0);
            else if (a.getEndTime() != null && now.isBefore(a.getEndTime())) a.setStatus(1);
            else a.setStatus(2);
        });
        return Result.success(list);
    }

    @PostMapping("/activities")
    public Result<Activity> createActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestBody Activity activity) {
        if (!checkAdmin(auth)) return unauth();
        activityMapper.insert(activity);
        // 写入缓存
        activityService.cacheActivityInfo(activity);
        log.info("[管理员] 创建活动 id={}, name={}", activity.getId(), activity.getActivityName());
        return Result.success(activity);
    }

    @PutMapping("/activities/{id}")
    public Result<String> updateActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id, @RequestBody Activity activity) {
        if (!checkAdmin(auth)) return unauth();
        activity.setId(id);
        activityMapper.updateById(activity);
        // 清除活动缓存 + 从数据库重新加载最新数据后缓存
        redisTemplate.delete("activity:info:" + id);
        Activity fresh = activityMapper.selectById(id);
        if (fresh != null) {
            activityService.cacheActivityInfo(fresh);
        }
        // 清除关联商品的缓存（商品里存了活动时间的快照）
        List<SeckillGoods> sgList = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>().eq(SeckillGoods::getActivityId, id));
        for (SeckillGoods sg : sgList) {
            redisTemplate.delete("goods:info:" + sg.getGoodsId());
        }
        log.info("[管理员] 更新活动 id={}, 已清除 {} 个关联商品缓存", id, sgList.size());
        return Result.success("更新成功");
    }

    @DeleteMapping("/activities/{id}")
    public Result<String> deleteActivity(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        int rows = activityMapper.deleteById(id);
        log.info("[管理员] 删除活动 id={}, 影响行数={}", id, rows);
        if (rows <= 0) {
            return Result.error("活动不存在或已被删除");
        }
        // 清除缓存
        redisTemplate.delete("activity:info:" + id);
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

        // 转换为含名称的 OrderVo
        List<OrderVo> vos = toOrderVoList(p.getRecords());

        Map<String, Object> data = new HashMap<>();
        data.put("records", vos);
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        return Result.success(data);
    }

    /** 批量查关联名称 */
    private List<OrderVo> toOrderVoList(List<SeckillOrder> orders) {
        if (orders.isEmpty()) return List.of();
        var userNames = userMapper.selectBatchIds(
                orders.stream().map(SeckillOrder::getUserId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        var goodsNames = goodsMapper.selectBatchIds(
                orders.stream().map(SeckillOrder::getGoodsId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Goods::getId, Goods::getGoodsName, (a, b) -> a));
        var activityNames = activityMapper.selectBatchIds(
                orders.stream().map(SeckillOrder::getActivityId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Activity::getId, Activity::getActivityName, (a, b) -> a));

        List<OrderVo> vos = new ArrayList<>();
        for (SeckillOrder o : orders) {
            OrderVo vo = new OrderVo();
            vo.setId(o.getId()); vo.setUserId(o.getUserId()); vo.setGoodsId(o.getGoodsId());
            vo.setActivityId(o.getActivityId()); vo.setOrderStatus(o.getOrderStatus());
            vo.setSeckillPrice(o.getSeckillPrice()); vo.setCreateTime(o.getCreateTime());
            vo.setPayTime(o.getPayTime());
            vo.setUserName(userNames.getOrDefault(o.getUserId(), "用户" + o.getUserId()));
            vo.setGoodsName(goodsNames.getOrDefault(o.getGoodsId(), "商品" + o.getGoodsId()));
            vo.setActivityName(activityNames.getOrDefault(o.getActivityId(), "活动" + o.getActivityId()));
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 活动关联秒杀商品 ====================

    @PostMapping("/activities/{activityId}/goods/{goodsId}")
    public Result<SeckillGoods> addSeckillGoods(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long activityId, @PathVariable Long goodsId,
            @RequestBody SeckillGoods sg) {
        if (!checkAdmin(auth)) return unauth();
        // 删除旧关联
        seckillGoodsMapper.delete(new LambdaQueryWrapper<SeckillGoods>()
                .eq(SeckillGoods::getActivityId, activityId)
                .eq(SeckillGoods::getGoodsId, goodsId));
        sg.setActivityId(activityId);
        sg.setGoodsId(goodsId);
        seckillGoodsMapper.insert(sg);
        // 预热库存（直接写入，管理员配置的初始库存应覆盖旧值）
        String stockKey = "goods:stock:" + goodsId;
        redisTemplate.opsForValue().set(stockKey, sg.getSeckillStock());
        log.info("[管理员] 添加秒杀商品 activityId={}, goodsId={}, price={}, stock={}", activityId, goodsId, sg.getSeckillPrice(), sg.getSeckillStock());
        return Result.success(sg);
    }

    @DeleteMapping("/activities/{activityId}/goods/{goodsId}")
    public Result<String> removeSeckillGoods(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long activityId, @PathVariable Long goodsId) {
        if (!checkAdmin(auth)) return unauth();
        seckillGoodsMapper.delete(new LambdaQueryWrapper<SeckillGoods>()
                .eq(SeckillGoods::getActivityId, activityId)
                .eq(SeckillGoods::getGoodsId, goodsId));
        log.info("[管理员] 移除秒杀商品 activityId={}, goodsId={}", activityId, goodsId);
        return Result.success("已移除");
    }

    /** 获取活动下的秒杀商品列表 */
    @GetMapping("/activities/{activityId}/goods")
    public Result<List<SeckillGoods>> getActivityGoods(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long activityId) {
        if (!checkAdmin(auth)) return unauth();
        List<SeckillGoods> list = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>().eq(SeckillGoods::getActivityId, activityId));
        return Result.success(list);
    }

    // ==================== 商品图片管理 ====================

    @GetMapping("/goods/{goodsId}/images")
    public Result<List<GoodsImage>> listGoodsImages(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long goodsId) {
        if (!checkAdmin(auth)) return unauth();
        List<GoodsImage> list = goodsImageMapper.selectList(
                new LambdaQueryWrapper<GoodsImage>()
                        .eq(GoodsImage::getGoodsId, goodsId)
                        .orderByAsc(GoodsImage::getSortOrder));
        return Result.success(list);
    }

    @PostMapping("/goods/{goodsId}/images")
    public Result<GoodsImage> addGoodsImage(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long goodsId,
            @RequestBody GoodsImage image) {
        if (!checkAdmin(auth)) return unauth();
        image.setGoodsId(goodsId);
        goodsImageMapper.insert(image);
        return Result.success(image);
    }

    @DeleteMapping("/goods/images/{id}")
    public Result<String> deleteGoodsImage(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        goodsImageMapper.deleteById(id);
        return Result.success("删除成功");
    }

    // ==================== 评论管理 ====================

    @GetMapping("/reviews")
    public Result<Map<String, Object>> listReviews(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!checkAdmin(auth)) return unauth();
        Page<GoodsReview> p = new Page<>(page, size);
        p = goodsReviewMapper.selectPage(p,
                new LambdaQueryWrapper<GoodsReview>().orderByDesc(GoodsReview::getCreateTime));
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    @DeleteMapping("/reviews/{id}")
    public Result<String> deleteReview(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        if (!checkAdmin(auth)) return unauth();
        goodsReviewMapper.deleteById(id);
        return Result.success("删除成功");
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
