package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动实体，映射 t_seckill_activity 表
 */
@Data
@TableName("t_seckill_activity")
public class Activity {

    @TableId
    private Long id;

    /** 活动名称 */
    private String activityName;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 0-未开始 1-进行中 2-已结束 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
