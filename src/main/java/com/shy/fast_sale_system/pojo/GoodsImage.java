package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 商品图片，映射 t_goods_image 表
 */
@Data
@TableName("t_goods_image")
public class GoodsImage {
    @TableId
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Long goodsId;

    private String imageUrl;

    private Integer sortOrder;
}
