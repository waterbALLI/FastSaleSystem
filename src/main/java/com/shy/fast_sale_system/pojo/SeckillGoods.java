package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_seckill_goods")
public class SeckillGoods {
    @TableId
    private Long id;

    private Long activityId;

    private Long goodsId;

    private Double seckillPrice;

    private Integer seckillStock;

    private Integer limitPerUser;

    private Integer version;
}
