package com.shy.fast_sale_system.vo;

import com.shy.fast_sale_system.pojo.Goods;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsVo extends Goods {

    /** 秒杀价格（可不同于原价） */
    private Double seckillPrice;

    /** Redis 中实时剩余库存 */
    private Integer stockCount;

    /** 秒杀总库存（用于进度条分母） */
    private Integer seckillStock;

    /** 所属活动ID */
    private Long activityId;

    /** 活动开始时间 */
    private LocalDateTime activityStartTime;

    /** 活动结束时间 */
    private LocalDateTime activityEndTime;

    /** 活动状态：0-未开始 1-进行中 2-已结束 */
    private Integer activityStatus;

    /** 每人限购数量 */
    private Integer limitPerUser;
}
