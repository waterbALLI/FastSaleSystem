package com.shy.fast_sale_system.service;

public interface PreHeatService {
    /**
     * 手动预热：将所有秒杀商品、活动信息从 MySQL 重新加载到 Redis
     */
    void preHeat();
}
