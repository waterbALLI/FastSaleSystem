-- ==========================================
-- Fast Sale System 数据库初始化脚本 v2
-- 数据库: fast_sale
-- ==========================================

CREATE DATABASE IF NOT EXISTS fast_sale
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE fast_sale;

-- ==========================================
-- 1. 商品基础表（与秒杀活动解耦）
-- ==========================================
DROP TABLE IF EXISTS t_seckill_order;
DROP TABLE IF EXISTS t_seckill_goods;
DROP TABLE IF EXISTS t_seckill_activity;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_goods;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_goods (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY  COMMENT '商品ID',
    goods_name   VARCHAR(200)  NOT NULL                    COMMENT '商品名称',
    goods_title  VARCHAR(500)  DEFAULT ''                  COMMENT '商品描述/副标题',
    goods_img    VARCHAR(500)  DEFAULT ''                  COMMENT '主图URL',
    goods_detail TEXT                                     COMMENT '商品详情(富文本)',
    goods_price  DECIMAL(10,2) NOT NULL                   COMMENT '原价',
    goods_stock  INT           NOT NULL DEFAULT 0         COMMENT '普通库存',
    is_deleted   TINYINT       NOT NULL DEFAULT 0         COMMENT '逻辑删除 0-否 1-是',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ==========================================
-- 2. 用户表
-- ==========================================
CREATE TABLE t_user (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    nickname        VARCHAR(50)  NOT NULL                 COMMENT '昵称',
    phone           VARCHAR(20)  DEFAULT ''               COMMENT '手机号',
    password_hash   VARCHAR(128) NOT NULL                 COMMENT '密码哈希(BCrypt)',
    avatar_url      VARCHAR(500) DEFAULT ''               COMMENT '头像URL',
    is_deleted      TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    register_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==========================================
-- 3. 秒杀活动表
-- ==========================================
CREATE TABLE t_seckill_activity (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '活动ID',
    activity_name VARCHAR(200) NOT NULL                   COMMENT '活动名称',
    start_time    DATETIME     NOT NULL                   COMMENT '活动开始时间',
    end_time      DATETIME     NOT NULL                   COMMENT '活动结束时间',
    status        TINYINT      NOT NULL DEFAULT 0         COMMENT '0-未开始 1-进行中 2-已结束',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_time_status (start_time, end_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- ==========================================
-- 4. 秒杀商品表（活动-商品关联，携带秒杀专属信息）
-- ==========================================
CREATE TABLE t_seckill_goods (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    activity_id     BIGINT        NOT NULL                 COMMENT '所属活动ID',
    goods_id        BIGINT        NOT NULL                 COMMENT '基础商品ID',
    seckill_price   DECIMAL(10,2) NOT NULL                 COMMENT '秒杀价',
    seckill_stock   INT           NOT NULL                 COMMENT '秒杀库存(从基础库存划拨)',
    limit_per_user  INT           NOT NULL DEFAULT 1       COMMENT '每人限购数量',
    version         INT           NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity (activity_id),
    UNIQUE KEY uk_activity_goods (activity_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- ==========================================
-- 5. 秒杀订单表（核心：唯一索引防重）
-- ==========================================
CREATE TABLE t_seckill_order (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    user_id       BIGINT        NOT NULL                   COMMENT '用户ID',
    goods_id      BIGINT        NOT NULL                   COMMENT '基础商品ID',
    activity_id   BIGINT        NOT NULL                   COMMENT '所属活动ID',
    order_status  TINYINT       NOT NULL DEFAULT 0         COMMENT '0-待支付 1-已支付 2-已取消 3-已退款',
    seckill_price DECIMAL(10,2) NOT NULL                   COMMENT '成交秒杀价(快照)',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time      DATETIME      DEFAULT NULL               COMMENT '支付时间',
    UNIQUE KEY uk_user_activity_goods (user_id, activity_id, goods_id),
    INDEX idx_user (user_id),
    INDEX idx_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- ==========================================
-- 预置测试数据
-- ==========================================

-- 商品
INSERT INTO t_goods (goods_name, goods_title, goods_price, goods_stock) VALUES
    ('iPhone 15',         'Apple iPhone 15 128GB',      5999.00, 500),
    ('华为 Mate 60 Pro',   'HUAWEI Mate 60 Pro 512GB',   6999.00, 300),
    ('AirPods Pro',       'Apple AirPods Pro 第二代',    1899.00, 800);

-- 用户（密码为 123456 的 BCrypt 哈希，正式环境请用真实哈希替换）
-- 注：暂时保留明文以兼容现有 User.java 字段名，上线前务必改为 password_hash
INSERT INTO t_user (nickname, phone, password_hash) VALUES
    ('测试用户_华宇', '13800000001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'),
    ('测试用户_张三', '13800000002', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi');

-- 秒杀活动（时间覆盖当前，确保测试时活动可命中）
INSERT INTO t_seckill_activity (activity_name, start_time, end_time, status) VALUES
    ('618 数码秒杀专场', '2026-06-01 00:00:00', '2026-06-30 23:59:59', 1);

-- 秒杀商品（关联活动 + 商品，设定秒杀价和秒杀库存）
INSERT INTO t_seckill_goods (activity_id, goods_id, seckill_price, seckill_stock, limit_per_user) VALUES
    (1, 1, 3999.00, 100, 1),   -- iPhone 15: 秒杀价3999, 100件, 限购1
    (1, 2, 4999.00,  50, 1),   -- 华为:     秒杀价4999,  50件, 限购1
    (1, 3,  999.00, 200, 2);   -- AirPods:  秒杀价 999, 200件, 限购2
