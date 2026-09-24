-- 普通券"一人一张"预检：挡住重复请求，不碰 DB（§5.2.5）
-- 返回码与 StatusConstants.SeckillCode 对齐：0 可领 / 2 已领取过
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderKey = 'voucher:order:' .. voucherId
if (redis.call('sismember', orderKey, userId) == 1) then return 2 end
redis.call('sadd', orderKey, userId)
return 0
