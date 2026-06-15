package com.shy.fast_sale_system.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shy.fast_sale_system.mapper.ActivityMapper;
import com.shy.fast_sale_system.pojo.Activity;
import com.shy.fast_sale_system.service.ActivityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 本地缓存：最大 100 个活动，写入后 1 分钟过期 */
    private final Cache<Long, Activity> localCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    @Override
    public Activity getActivityById(Long activityId) {
        // 1. 本地缓存
        Activity activity = localCache.getIfPresent(activityId);
        if (activity != null) return activity;

        // 2. Redis 缓存
        String redisKey = "activity:info:" + activityId;
        activity = (Activity) redisTemplate.opsForValue().get(redisKey);
        if (activity != null) {
            localCache.put(activityId, activity);
            return activity;
        }

        // 3. 数据库
        activity = activityMapper.selectById(activityId);
        if (activity != null) {
            cacheActivityInfo(activity);
        }
        return activity;
    }

    @Override
    public List<Activity> getActiveActivities() {
        // 简单实现：查全部，过滤时间窗口内的
        List<Activity> all = activityMapper.selectList(null);
        LocalDateTime now = LocalDateTime.now();
        return all.stream()
                .filter(a -> {
                    LocalDateTime start = a.getStartTime();
                    LocalDateTime end = a.getEndTime();
                    return start != null && end != null
                            && !now.isBefore(start) && now.isBefore(end);
                })
                .toList();
    }

    @Override
    public boolean isInTimeWindow(Long activityId) {
        Activity activity = getActivityById(activityId);
        if (activity == null) {
            log.warn("[时间窗口] activityId={} 活动不存在", activityId);
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = activity.getStartTime();
        LocalDateTime end = activity.getEndTime();

        if (start == null || end == null) {
            log.warn("[时间窗口] activityId={} 开始或结束时间为空", activityId);
            return false;
        }

        boolean inWindow = !now.isBefore(start) && now.isBefore(end);
        if (!inWindow) {
            log.info("[时间窗口拦截] activityId={}, 当前时间={}, 开始={}, 结束={}, 在窗口内={}",
                    activityId, now, start, end, false);
        }
        return inWindow;
    }

    @Override
    public void cacheActivityInfo(Activity activity) {
        String redisKey = "activity:info:" + activity.getId();
        // TTL = 活动结束时间 - 当前时间，确保活动结束后缓存自动过期
        LocalDateTime end = activity.getEndTime();
        long ttlSeconds = 3600; // 默认 1 小时
        if (end != null) {
            long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), end);
            if (seconds > 0) {
                ttlSeconds = seconds;
            }
        }
        redisTemplate.opsForValue().set(redisKey, activity, ttlSeconds, TimeUnit.SECONDS);
        localCache.put(activity.getId(), activity);
        log.info("[活动缓存] activityId={}, TTL={}秒", activity.getId(), ttlSeconds);
    }
}
