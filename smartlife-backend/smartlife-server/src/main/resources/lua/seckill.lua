-- 秒杀资格预检：库存 key 存在性 → 时间窗 → 库存 → 一人一单，全部通过才扣减并登记（§5.2.6）
-- 返回码与 StatusConstants.SeckillCode 一一对应，最易漏的是 -1：
--   0 成功 / 1 库存不足 / 2 重复领取 / 3 未开始 / 4 已结束 / -1 库存未预热
-- 当前时间与时间窗上下界均由 Java 传毫秒时间戳（ARGV[3..5]），不在 Lua 内取 time，便于单测固定时间。
-- key 前缀与 RedisConstants.SECKILL_STOCK_KEY / SECKILL_ORDER_KEY 保持一致（Lua 引用不了 Java 常量）
local voucherId = ARGV[1]
local userId = ARGV[2]
local now = tonumber(ARGV[3])
local beginTime = tonumber(ARGV[4])
local endTime = tonumber(ARGV[5])

local stockKey = 'seckill:stock:' .. voucherId
if (redis.call('exists', stockKey) == 0) then return -1 end
if (now < beginTime) then return 3 end
if (now > endTime) then return 4 end
if (tonumber(redis.call('get', stockKey)) <= 0) then return 1 end

local orderKey = 'seckill:order:' .. voucherId
if (redis.call('sismember', orderKey, userId) == 1) then return 2 end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
