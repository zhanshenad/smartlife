-- P3 交易主链路的测试数据：分类 + 1 号店(百味快餐)的菜品/口味/套餐。
-- 前置：先跑过 test-data-shop.sql（百味快餐 id=1）。可重复执行。
-- 注意：本脚本用 TRUNCATE，只适合开发库（全库仅测试店在用这些表）。

-- 分类：平台级字典
TRUNCATE TABLE `tb_category`;
INSERT INTO `tb_category` (`type`, `name`, `sort`, `status`) VALUES
    (1, '主食',   1, 1),
    (1, '热菜',   2, 1),
    (1, '凉菜',   3, 1),
    (1, '饮品',   4, 1),
    (2, '单人套餐', 1, 1),
    (2, '双人套餐', 2, 1);
SET @cat_staple = (SELECT id FROM `tb_category` WHERE `type` = 1 AND `name` = '主食');
SET @cat_hot    = (SELECT id FROM `tb_category` WHERE `type` = 1 AND `name` = '热菜');
SET @cat_cold   = (SELECT id FROM `tb_category` WHERE `type` = 1 AND `name` = '凉菜');
SET @cat_drink  = (SELECT id FROM `tb_category` WHERE `type` = 1 AND `name` = '饮品');
SET @cat_set1   = (SELECT id FROM `tb_category` WHERE `type` = 2 AND `name` = '单人套餐');
SET @cat_set2   = (SELECT id FROM `tb_category` WHERE `type` = 2 AND `name` = '双人套餐');

-- 菜品：百味快餐(id=1)，价格为分。stock 故意有大有小，方便验证下单扣减与不足报错
TRUNCATE TABLE `tb_dish`;
INSERT INTO `tb_dish`
    (`shop_id`, `name`,           `category_id`, `price`, `stock`, `status`, `description`, `image`)
VALUES
    (1, '招牌牛肉饭',     @cat_staple, 2200, 50,  1, '牛腩慢炖两小时，浇汁拌饭', ''),
    (1, '香辣鸡腿堡',     @cat_staple, 1800, 60,  1, '现炸鸡腿排，微辣', ''),
    (1, '蛋炒饭',         @cat_staple, 1200, 100, 1, '隔夜饭旺火快炒', ''),
    (1, '酸辣土豆丝',     @cat_hot,    1200, 80,  1, '家常下饭', ''),
    (1, '红烧排骨',       @cat_hot,    3200, 30,  1, '本店招牌硬菜', ''),
    (1, '麻婆豆腐',       @cat_hot,    1500, 3,   1, '库存仅 3 份，测扣减不足', ''),
    (1, '凉拌黄瓜',       @cat_cold,   800,  90,  1, '清爽解腻', ''),
    (1, '皮蛋豆腐',       @cat_cold,   1000, 70,  1, '淋上秘制料汁', ''),
    (1, '冰镇酸梅汤',     @cat_drink,  600,  200, 1, '古法熬制', ''),
    (1, '现磨豆浆',       @cat_drink,  500,  0,   1, '售罄样例：库存 0', ''),
    (1, '停售测试-薯条',  @cat_hot,    900,  40,  0, 'status=0 停售样例', '');

-- 口味：辣度给两道热菜，甜度给酸梅汤
TRUNCATE TABLE `tb_dish_flavor`;
INSERT INTO `tb_dish_flavor` (`dish_id`, `name`, `value`) VALUES
    ((SELECT id FROM `tb_dish` WHERE `shop_id` = 1 AND `name` = '麻婆豆腐'), '辣度', '["不辣","微辣","中辣","重辣"]'),
    ((SELECT id FROM `tb_dish` WHERE `shop_id` = 1 AND `name` = '酸辣土豆丝'), '辣度', '["微辣","中辣"]'),
    ((SELECT id FROM `tb_dish` WHERE `shop_id` = 1 AND `name` = '冰镇酸梅汤'), '甜度', '["正常糖","半糖","无糖"]');

-- 套餐 + 关联（setmeal_dish 冗余菜品名与单价快照）
TRUNCATE TABLE `tb_setmeal`;
INSERT INTO `tb_setmeal`
    (`shop_id`, `name`,           `category_id`, `price`, `stock`, `status`, `description`, `image`)
VALUES
    (1, '一人食实惠套餐', @cat_set1, 3000, 40, 1, '牛肉饭+酸梅汤，比单点省 3 元', ''),
    (1, '双人欢聚套餐',   @cat_set2, 5800, 20, 1, '两份主食+热菜+凉菜+饮品', '');

TRUNCATE TABLE `tb_setmeal_dish`;
INSERT INTO `tb_setmeal_dish` (`setmeal_id`, `dish_id`, `name`, `price`, `copies`)
SELECT s.id, d.id, d.name, d.price, 1
FROM `tb_setmeal` s
JOIN `tb_dish` d ON d.`shop_id` = s.`shop_id`
WHERE s.`shop_id` = 1
  AND (
    (s.`name` = '一人食实惠套餐' AND d.`name` IN ('招牌牛肉饭', '冰镇酸梅汤'))
    OR
    (s.`name` = '双人欢聚套餐' AND d.`name` IN ('招牌牛肉饭', '香辣鸡腿堡', '红烧排骨', '凉拌黄瓜', '冰镇酸梅汤'))
  );

SELECT '✅ 菜品测试数据就绪：6 分类 + 11 菜品(含停售/售罄样例) + 3 口味 + 2 套餐' AS result;
