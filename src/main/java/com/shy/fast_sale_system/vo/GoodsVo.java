package com.shy.fast_sale_system.vo;

import com.shy.fast_sale_system.pojo.Goods;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsVo extends Goods {

    /** 秒杀价格（可不同于原价） */
    private Double seckillPrice;

    /** Redis 中实时剩余库存 */
    private Integer stockCount;

    /** 秒杀总库存（用于进度条分母） */
    private Integer seckillStock;
}
