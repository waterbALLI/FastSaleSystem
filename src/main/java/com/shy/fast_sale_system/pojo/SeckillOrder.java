package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_order")
public class SeckillOrder {

    @TableId
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 基础商品ID */
    private Long goodsId;

    /** 所属活动ID */
    private Long activityId;

    /** 0-待支付 1-已支付 2-已取消 3-已退款 */
    private Integer orderStatus;

    /** 成交秒杀价 */
    private BigDecimal seckillPrice;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;
}
