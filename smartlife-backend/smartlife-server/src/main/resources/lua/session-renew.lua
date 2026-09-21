-- 会话按阈值续期：剩余 TTL 低于阈值才续，避免高频接口把 Redis 变成写密集型。
--
-- KEYS[1] = login:token:{jti}    会话 Hash
-- KEYS[2] = login:user:{userId}  反向索引 Set
-- ARGV[1] = 续期阈值（秒）
-- ARGV[2] = 续期后的新 TTL（秒）
-- 返回：调用前的剩余 TTL（-1 键不存在、-2 键无 TTL，两者都会走到 expire）

local ttl = redis.call('ttl', KEYS[1])
if ttl < tonumber(ARGV[1]) then
    redis.call('expire', KEYS[1], ARGV[2])
    redis.call('expire', KEYS[2], ARGV[2])
end
return ttl
