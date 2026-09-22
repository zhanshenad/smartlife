-- 解锁：锁值与自己的 token 一致才删除。
-- GET 与 DEL 必须原子：分两步的话，GET 比对通过后锁恰好过期易主，DEL 删掉的就是别人的锁。
-- KEYS[1] = 锁 key
-- ARGV[1] = 抢锁时写入的 token

if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
