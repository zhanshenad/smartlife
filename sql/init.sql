-- ============================================================================
-- 智慧生活 SmartLife · 建表脚本
--
-- 设计约定（详见《重构计划》§6.2）：
--   1. 命名对齐苍穹外卖：外键字段带 _id 后缀，时间字段统一 create_time / update_time
--   2. 所有金额用 INT 存「分」，不用 DECIMAL / FLOAT —— 浮点误差在钱上是事故
--   3. 唯一索引必须建在业务约束上，不能只靠代码判重
--   4. 券按「领券模型」建：tb_voucher 用 threshold + actual_value，没有 pay_value
--
-- 幂等：可重复执行（DROP + CREATE），本地开发随时重置
-- 执行：mysql -uroot -p < init.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `smartlife`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `smartlife`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 一、用户与权限
-- ============================================================================

DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `openid`      varchar(45)  DEFAULT NULL COMMENT '微信用户唯一标识，本项目未接入微信，保留字段',
    `phone`       varchar(11)  NOT NULL COMMENT '手机号，登录账号',
    `password`    varchar(128) DEFAULT '' COMMENT 'BCrypt 加密存储，绝不明文',
    `nick_name`   varchar(32)  DEFAULT '' COMMENT '昵称',
    `icon`        varchar(500) DEFAULT '' COMMENT '头像',
    `sex`         tinyint      DEFAULT NULL COMMENT '0 女 1 男',
    `role`        tinyint      NOT NULL DEFAULT 1 COMMENT '1 用户端 2 商家端 3 管理端',
    `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '0 禁用 1 启用。封禁时同步 INCR 会话版本号，令已签发 token 立即失效',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_role_status` (`role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表（三端共用，靠 role 区分身份）';

-- ============================================================================
-- 二、店铺
-- ============================================================================

DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `merchant_id` bigint       NOT NULL COMMENT '店主（role=2 的用户 id）',
    `name`        varchar(128) NOT NULL COMMENT '店铺名称',
    `type_id`     bigint       NOT NULL COMMENT '关联 tb_shop_type.id',
    `images`      varchar(1024) DEFAULT NULL COMMENT '店铺图片，多张以英文逗号分隔',
    `area`        varchar(128) DEFAULT NULL COMMENT '商圈，如「陆家嘴」',
    `address`     varchar(255) NOT NULL COMMENT '详细地址',
    `x`           double       NOT NULL COMMENT '经度',
    `y`           double       NOT NULL COMMENT '纬度',
    `avg_price`   int          DEFAULT NULL COMMENT '人均消费，单位分',
    `sold`        int          NOT NULL DEFAULT 0 COMMENT '销量',
    `comments`    int          NOT NULL DEFAULT 0 COMMENT '评论数',
    `score`       int          NOT NULL DEFAULT 0 COMMENT '评分，乘 10 保存，如 47 = 4.7 分',
    `open_hours`  varchar(32)  DEFAULT NULL COMMENT '营业时间，如 10:00-22:00',
    `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '0 停业 1 营业（Redis 存状态，DB 为落点）',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    -- 一个商家只能有一家店：商家端「只能操作自己的店」的归属校验基础
    UNIQUE KEY `uk_merchant_id` (`merchant_id`),
    KEY `idx_type_id` (`type_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺';

DROP TABLE IF EXISTS `tb_shop_type`;
CREATE TABLE `tb_shop_type` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(32) DEFAULT NULL COMMENT '类型名称',
    `icon`        varchar(255) DEFAULT NULL COMMENT '图标',
    `sort`        int         NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` datetime    DEFAULT NULL,
    `update_time` datetime    DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺类型（数据量小且几乎不变，典型全量缓存对象）';

DROP TABLE IF EXISTS `tb_category`;
CREATE TABLE `tb_category` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type`        int         DEFAULT NULL COMMENT '1 菜品分类 2 套餐分类',
    `name`        varchar(32) NOT NULL COMMENT '分类名称',
    `sort`        int         NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      int         DEFAULT 1 COMMENT '0 禁用 1 启用',
    `create_time` datetime    DEFAULT NULL,
    `update_time` datetime    DEFAULT NULL,
    `create_user` bigint      DEFAULT NULL COMMENT '创建人',
    `update_user` bigint      DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (`id`),
    -- 平台级字典，由管理端统一维护；唯一性按 (类型, 名称) 约束，
    -- 否则「招牌」这种词无法同时出现在菜品分类和套餐分类里
    UNIQUE KEY `uk_type_name` (`type`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜品/套餐分类（平台级字典，非店铺私有）';

-- ============================================================================
-- 三、商品
-- ============================================================================

DROP TABLE IF EXISTS `tb_dish`;
CREATE TABLE `tb_dish` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id`     bigint       NOT NULL COMMENT '归属店铺，商家端写操作必须校验此字段',
    `name`        varchar(32)  NOT NULL COMMENT '菜品名称',
    `category_id` bigint       NOT NULL COMMENT '关联 tb_category.id',
    `price`       int          NOT NULL COMMENT '⚠️ 单位「分」',
    `image`       varchar(255) DEFAULT NULL COMMENT '图片',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `status`      int          DEFAULT 1 COMMENT '0 停售 1 起售',
    `stock`       int          NOT NULL DEFAULT 0 COMMENT '库存，下单时 UPDATE ... WHERE stock >= n 原子扣减',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    `create_user` bigint       DEFAULT NULL,
    `update_user` bigint       DEFAULT NULL,
    PRIMARY KEY (`id`),
    -- 不同店铺可以重名，所以唯一性是 (店铺, 菜名) 而非全局
    UNIQUE KEY `uk_shop_name` (`shop_id`, `name`),
    KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜品';

DROP TABLE IF EXISTS `tb_dish_flavor`;
CREATE TABLE `tb_dish_flavor` (
    `id`      bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dish_id` bigint      NOT NULL COMMENT '菜品 id',
    `name`    varchar(32) DEFAULT NULL COMMENT '口味名称，如「辣度」',
    `value`   varchar(255) DEFAULT NULL COMMENT '口味选项，JSON 数组字符串',
    PRIMARY KEY (`id`),
    KEY `idx_dish_id` (`dish_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜品口味';

DROP TABLE IF EXISTS `tb_setmeal`;
CREATE TABLE `tb_setmeal` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id`     bigint       NOT NULL COMMENT '归属店铺',
    `category_id` bigint       NOT NULL COMMENT '关联 tb_category.id（type=2）',
    `name`        varchar(32)  NOT NULL COMMENT '套餐名称',
    `price`       int          NOT NULL COMMENT '⚠️ 单位「分」',
    `status`      int          DEFAULT 1 COMMENT '0 停售 1 起售',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `image`       varchar(255) DEFAULT NULL COMMENT '图片',
    `stock`       int          NOT NULL DEFAULT 0 COMMENT '库存，语义同 tb_dish.stock',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    `create_user` bigint       DEFAULT NULL,
    `update_user` bigint       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shop_name` (`shop_id`, `name`),
    KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '套餐';

DROP TABLE IF EXISTS `tb_setmeal_dish`;
CREATE TABLE `tb_setmeal_dish` (
    `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `setmeal_id` bigint      DEFAULT NULL COMMENT '套餐 id',
    `dish_id`    bigint      DEFAULT NULL COMMENT '菜品 id',
    `name`       varchar(32) DEFAULT NULL COMMENT '冗余：菜品名称快照',
    `price`      int         DEFAULT NULL COMMENT '冗余：菜品单价快照，单位分',
    `copies`     int         DEFAULT NULL COMMENT '份数',
    PRIMARY KEY (`id`),
    KEY `idx_setmeal_id` (`setmeal_id`),
    KEY `idx_dish_id` (`dish_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '套餐-菜品关联（name/price 为冗余快照）';

-- ============================================================================
-- 四、交易
-- ============================================================================

DROP TABLE IF EXISTS `tb_orders`;
CREATE TABLE `tb_orders` (
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `number`                  varchar(50)  NOT NULL COMMENT '订单号，RedisIdWorker 生成',
    `status`                  tinyint      NOT NULL DEFAULT 1 COMMENT '1 待支付 2 待接单 3 已接单 4 派送中 5 已完成 6 已取消',
    `user_id`                 bigint       NOT NULL COMMENT '下单用户',
    `shop_id`                 bigint       NOT NULL COMMENT '归属店铺，券核销时须与 tb_voucher.shop_id 一致',
    `order_time`              datetime     NOT NULL COMMENT '下单时间（业务时间，报表按它分组）',
    `checkout_time`           datetime     DEFAULT NULL COMMENT '结账时间',
    `pay_method`              int          NOT NULL DEFAULT 1 COMMENT '1 微信 2 支付宝（本项目为模拟支付）',
    `pay_status`              tinyint      NOT NULL DEFAULT 0 COMMENT '0 未支付 1 已支付 2 退款',
    `amount`                  int          NOT NULL DEFAULT 0 COMMENT '订单原价（菜品/套餐小计之和），单位分',
    `discount_amount`         int          NOT NULL DEFAULT 0 COMMENT '券抵扣金额，单位分',
    `pay_amount`              int          NOT NULL DEFAULT 0 COMMENT '实付 = amount - discount_amount，下限 0',
    `remark`                  varchar(100) DEFAULT NULL COMMENT '备注',
    `phone`                   varchar(11)  DEFAULT NULL COMMENT '收货人手机号（下单快照）',
    `address`                 varchar(255) DEFAULT NULL COMMENT '收货地址（下单快照）',
    `user_name`               varchar(32)  DEFAULT NULL COMMENT '用户名（下单快照）',
    `consignee`               varchar(32)  DEFAULT NULL COMMENT '收货人（下单快照）',
    `cancel_reason`           varchar(255) DEFAULT NULL COMMENT '取消原因',
    `rejection_reason`        varchar(255) DEFAULT NULL COMMENT '商家拒单原因',
    `cancel_time`             datetime     DEFAULT NULL COMMENT '取消时间',
    `estimated_delivery_time` datetime     DEFAULT NULL COMMENT '预计送达时间',
    `delivery_status`         tinyint      NOT NULL DEFAULT 1 COMMENT '1 立即送出 0 选择具体时间',
    `delivery_time`           datetime     DEFAULT NULL COMMENT '实际送达时间',
    `pack_amount`             int          DEFAULT NULL COMMENT '打包费，单位分',
    `tableware_number`        int          DEFAULT NULL COMMENT '餐具数量',
    `tableware_status`        tinyint      NOT NULL DEFAULT 1 COMMENT '1 按餐量提供 0 选择具体数量',
    `create_time`             datetime     DEFAULT NULL,
    `update_time`             datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    -- 防重复下单的最后一道硬约束：上游漏了什么检查，重复号都插不进来
    UNIQUE KEY `uk_number` (`number`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_shop_status` (`shop_id`, `status`),
    KEY `idx_status_order_time` (`status`, `order_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

DROP TABLE IF EXISTS `tb_order_detail`;
CREATE TABLE `tb_order_detail` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    bigint      NOT NULL COMMENT '订单 id',
    `name`        varchar(32) DEFAULT NULL COMMENT '冗余：菜品/套餐名称快照',
    `image`       varchar(255) DEFAULT NULL COMMENT '冗余：图片快照',
    `dish_id`     bigint      DEFAULT NULL COMMENT '菜品 id（与 setmeal_id 二选一）',
    `setmeal_id`  bigint      DEFAULT NULL COMMENT '套餐 id',
    `dish_flavor` varchar(50) DEFAULT NULL COMMENT '口味，如「微辣,不要香菜」',
    `number`      int         NOT NULL DEFAULT 1 COMMENT '数量',
    `amount`      int         NOT NULL COMMENT '该明细小计金额，单位分',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细（字段均为下单时刻快照）';

-- 购物车：纯 MySQL，不加缓存（定案见《重构计划》§6.2 第 7 条）。
--    缓存的三条适用条件（热点 / 读多写少 / 可容忍不一致）购物车一条都不满足，
--    做了只会每次读都 miss。加购走 INSERT ... ON DUPLICATE KEY UPDATE number = number + 1，
--    一条 SQL 原子完成"不存在则插入、存在则 +1"。
--    dish_id/setmeal_id/dish_flavor 用 0/空串 代替 NULL：
--    MySQL 唯一索引对 NULL 不去重（NULL != NULL），留 NULL 就防不住重复加购。
DROP TABLE IF EXISTS `tb_shopping_cart`;
CREATE TABLE `tb_shopping_cart` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint      NOT NULL COMMENT '用户 id',
    `name`        varchar(32) DEFAULT NULL COMMENT '商品名称',
    `image`       varchar(255) DEFAULT NULL COMMENT '图片',
    `dish_id`     bigint      NOT NULL DEFAULT 0 COMMENT '菜品 id（加购套餐时为 0）',
    `setmeal_id`  bigint      NOT NULL DEFAULT 0 COMMENT '套餐 id（加购菜品时为 0）',
    `dish_flavor` varchar(50) NOT NULL DEFAULT '' COMMENT '口味',
    `number`      int         NOT NULL DEFAULT 1 COMMENT '数量',
    `amount`      int         NOT NULL COMMENT '单价，单位分',
    `create_time` datetime    DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_goods` (`user_id`, `dish_id`, `setmeal_id`, `dish_flavor`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车（纯 MySQL，不加缓存；加购用 DB 端原子自增）';

-- ============================================================================
-- 五、优惠券
-- ============================================================================

DROP TABLE IF EXISTS `tb_voucher`;
CREATE TABLE `tb_voucher` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id`      bigint       NOT NULL COMMENT '归属店铺。核销时须与 tb_orders.shop_id 一致，防 A 店券在 B 店抵扣',
    `title`        varchar(255) NOT NULL COMMENT '券标题',
    `sub_title`    varchar(255) DEFAULT NULL COMMENT '副标题',
    `rules`        varchar(1024) DEFAULT NULL COMMENT '使用规则',
    `threshold`    int          NOT NULL DEFAULT 0 COMMENT '使用门槛：订单满多少分可用，0 = 无门槛',
    `actual_value` int          NOT NULL COMMENT '抵扣金额，单位分',
    `type`         tinyint      NOT NULL DEFAULT 0 COMMENT '0 普通券 1 秒杀券',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '1 上架 2 下架 3 过期',
    `create_time`  datetime     DEFAULT NULL,
    `update_time`  datetime     DEFAULT NULL,
    `create_user`  bigint       DEFAULT NULL,
    `update_user`  bigint       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_type_status` (`type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '优惠券（领券模型：无 pay_value，改用 threshold）';

DROP TABLE IF EXISTS `tb_seckill_voucher`;
CREATE TABLE `tb_seckill_voucher` (
    `voucher_id`  bigint   NOT NULL COMMENT '与 tb_voucher.id 同值',
    `stock`       int      NOT NULL DEFAULT 0 COMMENT '权威库存。Redis 里的 seckill:stock:{id} 只是加速副本',
    `begin_time`  datetime NOT NULL COMMENT '生效时间，Lua 时间窗校验下界',
    `end_time`    datetime NOT NULL COMMENT '失效时间，Lua 时间窗校验上界',
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀券附加信息（与 tb_voucher 一对一）';

DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
    `id`          bigint   NOT NULL COMMENT '主键，RedisIdWorker 生成（Lua 执行前就要拿到，好塞进 MQ 消息）',
    `user_id`     bigint   NOT NULL COMMENT '用户 id',
    `voucher_id`  bigint   NOT NULL COMMENT '券 id',
    `status`      tinyint  NOT NULL DEFAULT 1 COMMENT '1 未使用 2 已使用。注意：领券模型只有这两个状态',
    `create_time` datetime DEFAULT NULL COMMENT '领取时间',
    `use_time`    datetime DEFAULT NULL COMMENT '核销时间',
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    -- ★ 并发防重的核心约束：一人一券。
    --   秒杀链路的幂等最终靠它兜底，Redis 的 Set/Lua 只是把无效请求挡在 DB 之前
    UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`),
    KEY `idx_voucher_id` (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '券订单（用户的券包）';

-- ============================================================================
-- 六、内容社交
-- ============================================================================

DROP TABLE IF EXISTS `tb_blog`;
CREATE TABLE `tb_blog` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id`     bigint        NOT NULL COMMENT '关联店铺',
    `user_id`     bigint        NOT NULL COMMENT '作者',
    `title`       varchar(255)  NOT NULL COMMENT '标题',
    `images`      varchar(2048) NOT NULL COMMENT '探店照片，最多 9 张，逗号分隔',
    `content`     varchar(2048) NOT NULL COMMENT '正文',
    `liked`       int           NOT NULL DEFAULT 0 COMMENT '点赞数（展示计数，权威在 Redis ZSet）',
    `comments`    int           NOT NULL DEFAULT 0 COMMENT '评论数',
    `create_time` datetime      DEFAULT NULL,
    `update_time` datetime      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '探店笔记';

DROP TABLE IF EXISTS `tb_blog_comments`;
CREATE TABLE `tb_blog_comments` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint       NOT NULL COMMENT '评论人',
    `blog_id`     bigint       NOT NULL COMMENT '笔记 id',
    `parent_id`   bigint       NOT NULL DEFAULT 0 COMMENT '所属一级评论 id，一级评论自身为 0',
    `answer_id`   bigint       DEFAULT NULL COMMENT '被回复的评论 id',
    `content`     varchar(255) NOT NULL COMMENT '内容',
    `liked`       int          DEFAULT 0 COMMENT '点赞数',
    `status`      tinyint      DEFAULT 0 COMMENT '0 正常 1 被举报 2 禁止查看',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_blog_id` (`blog_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记评论（两级结构）';

DROP TABLE IF EXISTS `tb_follow`;
CREATE TABLE `tb_follow` (
    `id`             bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        bigint   NOT NULL COMMENT '关注发起方',
    `follow_user_id` bigint   NOT NULL COMMENT '被关注者',
    `create_time`    datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    -- 防重复关注：Redis Set 是读路径，这里是权威
    UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
    KEY `idx_follow_user_id` (`follow_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系（Redis Set 为读路径，本表为持久化落点）';

-- 字段名刻意用 sign_year / sign_month / sign_date，
-- 而不是裸的 year / month / date —— 避免与 MySQL 关键字和内置函数名撞车
DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign` (
    `id`         bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    bigint   NOT NULL COMMENT '用户 id',
    `sign_year`  int      NOT NULL COMMENT '签到年',
    `sign_month` tinyint  NOT NULL COMMENT '签到月',
    `sign_date`  date     NOT NULL COMMENT '签到日期',
    `is_backup`  tinyint  DEFAULT 0 COMMENT '是否补签',
    PRIMARY KEY (`id`),
    -- 防重复签到：在线判断走 Redis BitMap，这里是持久化与最终校验
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '签到记录（在线查询走 Redis BitMap）';

-- ============================================================================
-- 七、平台治理
-- ============================================================================

DROP TABLE IF EXISTS `tb_merchant_apply`;
CREATE TABLE `tb_merchant_apply` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        bigint       NOT NULL COMMENT '申请人（role=2）',
    `shop_name`      varchar(128) NOT NULL COMMENT '拟用店铺名',
    `shop_type_id`   bigint       NOT NULL COMMENT '经营类型',
    `area`           varchar(128) DEFAULT NULL COMMENT '商圈',
    `address`        varchar(255) NOT NULL COMMENT '地址',
    `x`              double       DEFAULT NULL COMMENT '经度',
    `y`              double       DEFAULT NULL COMMENT '纬度',
    `contact_name`   varchar(32)  DEFAULT NULL COMMENT '联系人',
    `contact_phone`  varchar(11)  DEFAULT NULL COMMENT '联系电话',
    `license_images` varchar(1024) DEFAULT NULL COMMENT '资质图，多张逗号分隔',
    `status`         tinyint      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 已通过 2 已驳回',
    `audit_remark`   varchar(255) DEFAULT NULL COMMENT '审核意见，驳回时必填',
    `audit_user_id`  bigint       DEFAULT NULL COMMENT '审核人',
    `audit_time`     datetime     DEFAULT NULL COMMENT '审核时间',
    `shop_id`        bigint       DEFAULT NULL COMMENT '通过后创建的店铺 id',
    `create_time`    datetime     DEFAULT NULL,
    `update_time`    datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家入驻申请单（审核仅允许发生在 status=0，防重复建店）';

DROP TABLE IF EXISTS `tb_audit_log`;
CREATE TABLE `tb_audit_log` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_id`   bigint       NOT NULL COMMENT '操作人（管理员）id',
    `operator_name` varchar(32)  DEFAULT NULL COMMENT '冗余操作人名称：账号可能被删，日志署名要留存',
    `action`        varchar(64)  NOT NULL COMMENT '动作标识，如 APPROVE_MERCHANT / BAN_USER / KICK_SESSION',
    `target_type`   varchar(32)  DEFAULT NULL COMMENT '目标类型，如 USER / SHOP / VOUCHER',
    `target_id`     bigint       DEFAULT NULL COMMENT '目标 id',
    `detail`        varchar(2048) DEFAULT NULL COMMENT '变更明细 JSON（存变更前后快照，否则只能看到「改过」看不到「改成什么」）',
    `ip`            varchar(64)  DEFAULT NULL COMMENT '操作来源 IP',
    `create_time`   datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理端操作审计日志（只增不改）';

-- ============================================================================
-- 八、字典种子数据
--
-- 只放「字典类」数据（类型、分类）。用户/店铺/菜品/券这些业务数据不放这里：
--   账号密码需要 BCrypt 加密，SQL 里造不出合法哈希 —— 由 P1 的初始化逻辑或接口创建
-- ============================================================================

INSERT INTO `tb_shop_type` (`id`, `name`, `icon`, `sort`) VALUES
    (1, '美食',      '/types/ms.png',   1),
    (2, 'KTV',       '/types/KTV.png',  2),
    (3, '丽人·美发', '/types/lrmf.png', 3),
    (4, '健身运动',  '/types/jsyd.png', 4),
    (5, '按摩·足疗', '/types/amzl.png', 5),
    (6, '美容SPA',   '/types/spa.png',  6),
    (7, '亲子游乐',  '/types/qzyl.png', 7),
    (8, '酒吧',      '/types/jiuba.png', 8),
    (9, '轰趴馆',    '/types/hpg.png',  9),
    (10, '美睫·美甲', '/types/mjmj.png', 10);

INSERT INTO `tb_category` (`id`, `type`, `name`, `sort`, `status`) VALUES
    (11, 1, '酒水饮料', 10, 1),
    (12, 1, '传统主食', 20, 1),
    (13, 2, '人气套餐', 10, 1),
    (14, 2, '商务套餐', 20, 1),
    (15, 1, '特色蒸菜', 30, 1),
    (16, 1, '新鲜时蔬', 40, 1),
    (17, 1, '汤类',     50, 1);

SELECT '✅ smartlife 库初始化完成，共 20 张表' AS result;
