-- ============================================================================
-- P4 优惠券测试数据（真机验收 + 2000 并发压测用）
-- 商家：13800000002（id=47）拥有 1 号店；测试用户：13800000001（id=48）
-- 幂等：按固定 id 先删后插，可重复执行
-- 执行：mysql -uroot -p smartlife < test-data-voucher.sql
-- ============================================================================

USE `smartlife`;

-- 普通券：满 30 减 5（无限量，人人可领）
DELETE FROM `tb_voucher_order` WHERE voucher_id = 101;
DELETE FROM `tb_voucher` WHERE id = 101;
INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `threshold`, `actual_value`, `type`, `status`, `create_time`, `update_time`)
VALUES (101, 1, '新客满减券', '满 30 元减 5 元', '每人限领一张，下单满 30 元可用', 3000, 500, 0, 1, NOW(), NOW());

-- 秒杀券：无门槛 10 元券，库存 100，进行中（前 10 分钟开始，2 小时后结束）
-- 压测口径：2000 并发抢 100 库存，最终 tb_voucher_order 恰好 100 条、零超卖
DELETE FROM `tb_voucher_order` WHERE voucher_id = 102;
DELETE FROM `tb_seckill_voucher` WHERE voucher_id = 102;
DELETE FROM `tb_voucher` WHERE id = 102;
INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `threshold`, `actual_value`, `type`, `status`, `create_time`, `update_time`)
VALUES (102, 1, '限时秒杀券', '无门槛 10 元', '限量 100 张，先到先得，每人限抢一张', 0, 1000, 1, 1, NOW(), NOW());
INSERT INTO `tb_seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`, `create_time`, `update_time`)
VALUES (102, 100, DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), NOW());

-- 提醒：灌完本脚本后需重启服务（CacheWarmUpRunner 会把秒杀库存预热进 Redis），
-- 或手动执行 SET seckill:stock:102 100
