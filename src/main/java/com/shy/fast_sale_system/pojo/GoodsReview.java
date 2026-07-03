package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户评论，映射 t_goods_review 表
 */
@Data
@TableName("t_goods_review")
public class GoodsReview {
    @TableId
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Long goodsId;

    private Long userId;

    private Long orderId;

    private Integer rating;

    private String content;

    private LocalDateTime createTime;
}
