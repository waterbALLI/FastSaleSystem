package com.shy.fast_sale_system.pojo;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_goods") // 告诉 MyBatis-Plus，这个类对应数据库里的 t_goods 表
public class Goods {
    @TableId // 标记 id 为主键
    private Long id;

    private String goodsName;  // 对应数据库的 goods_name

    private Integer goodsStock; // 对应数据库的 goods_stock

    private Double goodsPrice;  // 对应数据库的 goods_price

    @TableField("goods_title")
    private String goodsTitle;

    @TableField("goods_img")
    private String goodsImg;

    @TableField("goods_detail")
    private String goodsDetail;

    @TableField("is_deleted")
    private Integer isDeleted;

    /** 品牌 */
    private String brand;

    /** 发货地 */
    @TableField("shipping_location")
    private String shippingLocation;

    /** 视频URL（最多一个） */
    @TableField("video_url")
    private String videoUrl;
}
