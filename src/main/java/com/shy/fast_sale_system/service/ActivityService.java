package com.shy.fast_sale_system.service;

import com.shy.fast_sale_system.pojo.Activity;

import java.util.List;

public interface ActivityService {

    /** 根据活动ID查询活动信息 */
    Activity getActivityById(Long activityId);

    /** 获取所有进行中的活动（startTime <= now < endTime） */
    List<Activity> getActiveActivities();

    /** 判断当前时间是否在活动时间窗口内 */
    boolean isInTimeWindow(Long activityId);

    /** 缓存活动信息到 Redis + 本地缓存 */
    void cacheActivityInfo(Activity activity);
}
