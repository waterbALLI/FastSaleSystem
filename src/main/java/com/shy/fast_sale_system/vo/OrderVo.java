package com.shy.fast_sale_system.vo;

import com.shy.fast_sale_system.pojo.SeckillOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单视图对象 — 含关联的用户名、商品名、活动名
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVo extends SeckillOrder {

    /** 用户昵称 */
    private String userName;

    /** 商品名称 */
    private String goodsName;

    /** 活动名称 */
    private String activityName;
}
