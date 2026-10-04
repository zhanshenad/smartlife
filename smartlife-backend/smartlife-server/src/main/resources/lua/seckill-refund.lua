-- 秒杀名额回补：库存 +1、摘掉占用者，两步原子完成。
-- 用在消息确认失败（broker 明确 nack 或路由不到队列）之后——消息没进队列就不会产生订单，
-- 预扣的名额必须还回去，否则用户被 seckill:order 的 SISMEMBER 卡住，永远重试不了。
-- 两步要原子：分开执行时若中间崩溃，会留下"库存还了但用户还占着"的错位状态。
-- KEYS[1] = seckill:stock:{voucherId}   KEYS[2] = seckill:order:{voucherId}
-- ARGV[1] = userId
-- key 前缀与 RedisConstants.SECKILL_STOCK_KEY / SECKILL_ORDER_KEY 保持一致（Lua 引用不了 Java 常量）
redis.call('incrby', KEYS[1], 1)
redis.call('srem', KEYS[2], ARGV[1])
return 1
