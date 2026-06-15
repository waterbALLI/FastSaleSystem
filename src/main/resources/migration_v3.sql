-- ==========================================
-- Fast Sale System 数据库迁移脚本 v3
-- 新增：商品品牌/发货地/视频 + 商品图片表 + 用户评论表
-- ==========================================

USE fast_sale;

-- 1. t_goods 新增字段
ALTER TABLE t_goods
    ADD COLUMN brand            VARCHAR(100) DEFAULT '' COMMENT '品牌' AFTER goods_detail,
    ADD COLUMN shipping_location VARCHAR(200) DEFAULT '' COMMENT '发货地' AFTER brand,
    ADD COLUMN video_url        VARCHAR(500) DEFAULT '' COMMENT '视频URL' AFTER shipping_location;

-- 2. 商品图片表
CREATE TABLE IF NOT EXISTS t_goods_image (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    goods_id   BIGINT       NOT NULL                COMMENT '商品ID',
    image_url  VARCHAR(500) NOT NULL                COMMENT '图片URL',
    sort_order INT          DEFAULT 0               COMMENT '排序',
    INDEX idx_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

-- 3. 用户评论表
CREATE TABLE IF NOT EXISTS t_goods_review (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    goods_id    BIGINT        NOT NULL               COMMENT '商品ID',
    user_id     BIGINT        NOT NULL               COMMENT '用户ID',
    order_id    BIGINT        DEFAULT NULL           COMMENT '订单ID',
    rating      TINYINT       NOT NULL DEFAULT 5     COMMENT '评分 1-5',
    content     VARCHAR(1000) DEFAULT ''             COMMENT '评论内容',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_goods (goods_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论表';

-- 4. 预置测试数据
INSERT INTO t_goods_image (goods_id, image_url, sort_order) VALUES
    (1, 'https://images.unsplash.com/photo-1592286927505-1def25115558?w=600', 0),
    (1, 'https://images.unsplash.com/photo-1592434134753-a70baf7979d5?w=600', 1),
    (1, 'https://images.unsplash.com/photo-1591337676887-a217a6970a5a?w=600', 2),
    (2, 'https://images.unsplash.com/photo-1598327105666-5b89351aff97?w=600', 0),
    (2, 'https://images.unsplash.com/photo-1595941069915-4ebc5197c14a?w=600', 1),
    (3, 'https://images.unsplash.com/photo-1588156979435-379b9d802b0d?w=600', 0);

INSERT INTO t_goods_review (goods_id, user_id, order_id, rating, content) VALUES
    (1, 1, NULL, 5, '物流很快，包装完好，手感很棒！'),
    (1, 2, NULL, 4, '性价比很高，就是发货稍微慢了点'),
    (1, 1, NULL, 5, '第二次购买了，品质稳定，推荐！'),
    (2, 1, NULL, 5, '拍照效果一流，华为的做工没得说'),
    (3, 2, NULL, 3, '音质不错但降噪一般，对得起价格');

-- 更新商品品牌和发货地
UPDATE t_goods SET brand = 'Apple', shipping_location = '广东深圳' WHERE id = 1;
UPDATE t_goods SET brand = 'HUAWEI', shipping_location = '广东东莞' WHERE id = 2;
UPDATE t_goods SET brand = 'Apple', shipping_location = '上海' WHERE id = 3;
