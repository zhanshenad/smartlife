-- 登录会话写入：会话 Hash 与反向索引 Set 一并原子落盘。
-- 分开执行时若进程在写入后崩溃，会留下一个没有 TTL 的永久会话键，会话将永不失效。
--
-- KEYS[1] = login:token:{jti}   会话 Hash
-- KEYS[2] = login:user:{userId} 反向索引 Set
-- ARGV[1] = TTL（秒）
-- ARGV[2] = jti
-- ARGV[3] = userId
-- ARGV[4] = role
-- ARGV[5] = nickname
-- 字段名须与 RedisConstants 的 SESSION_FIELD_* 保持一致

redis.call('hset', KEYS[1], 'userId', ARGV[3], 'role', ARGV[4], 'nickname', ARGV[5])
redis.call('expire', KEYS[1], ARGV[1])
redis.call('sadd', KEYS[2], ARGV[2])
redis.call('expire', KEYS[2], ARGV[1])
return 1
