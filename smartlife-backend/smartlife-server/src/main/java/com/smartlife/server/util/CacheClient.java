package com.smartlife.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.server.model.RedisData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用缓存工具，三件套防护的落点（§5.3）：
 * 雪崩 = set 随机 TTL；穿透 = 空值缓存；击穿 = 互斥锁（冷数据）/ 逻辑过期（热点）两套。
 * 锁均为 SETNX + TTL，锁值存 UUID、Lua 比对后删除，避免误删他人锁。
 */
@Component
public class CacheClient {

    //==================== 常量 ====================
    private static final Logger log = LoggerFactory.getLogger(CacheClient.class);

    /** 逻辑过期异步重建线程池。守护线程，重建失败也不拖住 JVM 退出 */
    private static final ThreadPoolExecutor REBUILD_EXECUTOR = new ThreadPoolExecutor(
            2, 5, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            r -> {
                Thread t = new Thread(r, "cache-rebuild");
                t.setDaemon(true);
                return t;
            });

    /** 比对锁值再删除，GET 与 DEL 的原子化 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CacheClient(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ==================== 写入 ====================

    /**
     * 普通 set：基础 TTL 再叠 [0, base/3) 的随机增量，错开同一批 key 的过期时间（防雪崩）。
     */
    public void set(String key, Object value, long baseTtl, TimeUnit unit) {
        long jitter = ThreadLocalRandom.current().nextLong(baseTtl / 3 + 1);
        redis.opsForValue().set(key, write(value), baseTtl + jitter, unit);
    }

    /** 逻辑过期 set：key 永不过期，到期时间写在 value 里（防击穿的热点方案） */
    public void setWithLogicalExpire(String key, Object value, long ttl, TimeUnit unit) {
        RedisData wrapper = new RedisData();
        wrapper.setExpireTime(LocalDateTime.now().plusNanos(unit.toNanos(ttl)));
        wrapper.setData(value);
        redis.opsForValue().set(key, write(wrapper));
    }

    // ==================== 查询：三套方案 ====================

    /**
     * 空值缓存防穿透。DB 也查不到时写入空串短 TTL，
     * 同一个不存在 key 的后续请求会被挡在 Redis，不再打 DB。
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json != null) {
            return json.isEmpty() ? null : read(json, type);
        }
        R r = dbFallback.apply(id);
        if (r == null) {
            writeNullMark(key);
            return null;
        }
        set(key, r, ttl, unit);
        return r;
    }

    /**
     * 互斥锁防击穿（冷数据方案，如店铺分类）。未命中时只放一个线程进库重建，
     * 其余自旋等待；拿锁后双检缓存。锁自带 TTL，持锁线程崩溃也能自愈。
     */
    public <R, ID> R queryWithMutex(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type,
                                    Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        while (true) {
            R cached = readCache(key, type);
            if (cached != null || isNullMarked(key)) {
                return cached;
            }
            String token = tryLock(lockKey, RedisConstants.LOCK_SHOP_TTL_SECONDS);
            if (token != null) {
                try {
                    // 双检：排队期间别的线程可能已经重建完
                    cached = readCache(key, type);
                    if (cached != null || isNullMarked(key)) {
                        return cached;
                    }
                    R r = dbFallback.apply(id);
                    if (r == null) {
                        writeNullMark(key);
                        return null;
                    }
                    set(key, r, ttl, unit);
                    return r;
                } finally {
                    unlock(lockKey, token);
                }
            }
            // 没抢到锁：别人正在重建，歇口气再来看一眼
            sleepQuietly(50);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
        }
    }

    /**
     * 逻辑过期防击穿（热点方案，如店铺详情）。命中即返回——旧值也返回，用户请求零等待；
     * 逻辑过期后只放一个后台线程异步重建。key 意外缺失（未预热 / flushdb）时
     * 同步抢锁重建自愈，不像原版那样直接返回 null。
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return rebuildOnMiss(key, id, type, dbFallback, ttl, unit);
        }
        if (json.isEmpty()) {
            return null;
        }

        RedisData wrapper = read(json, RedisData.class);
        R r = objectMapper.convertValue(wrapper.getData(), type);
        if (wrapper.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }
        // 已逻辑过期：抢到锁的线程丢给线程池异步重建，所有请求（含抢到锁的）先拿旧值走人
        String rebuildLockKey = RedisConstants.LOCK_REBUILD_KEY + id;
        String token = tryLock(rebuildLockKey, RedisConstants.LOCK_REBUILD_TTL_SECONDS);
        if (token != null) {
            REBUILD_EXECUTOR.submit(() -> {
                try {
                    R fresh = dbFallback.apply(id);
                    if (fresh != null) {
                        setWithLogicalExpire(key, fresh, ttl, unit);
                    } else {
                        // 源数据没了：删掉过期态的 key，让后续请求走 miss 自愈写空值，
                        // 否则 key 停在过期态、每个请求都触发一次重建提交
                        redis.delete(key);
                    }
                } catch (Exception e) {
                    log.error("缓存异步重建失败，旧值退避 60s：{}", key, e);
                    backoffQuietly(key, r);
                } finally {
                    unlock(rebuildLockKey, token);
                }
            });
        }
        return r;
    }

    /** 逻辑过期方案的 key 缺失自愈：同步抢锁查库回填，等待方短暂自旋 */
    private <R, ID> R rebuildOnMiss(String key, ID id, Class<R> type,
                                    Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String lockKey = RedisConstants.LOCK_REBUILD_KEY + id;
        while (true) {
            String token = tryLock(lockKey, RedisConstants.LOCK_REBUILD_TTL_SECONDS);
            if (token != null) {
                try {
                    String json = redis.opsForValue().get(key);
                    if (json != null) {
                        return json.isEmpty() ? null : parseLogical(json, type);
                    }
                    R r = dbFallback.apply(id);
                    if (r == null) {
                        writeNullMark(key);
                        return null;
                    }
                    setWithLogicalExpire(key, r, ttl, unit);
                    return r;
                } finally {
                    unlock(lockKey, token);
                }
            }
            sleepQuietly(50);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
        }
    }

    /** 删缓存（Cache Aside 的"改库后删"动作） */
    public void delete(String key) {
        redis.delete(key);
    }

    /** 读单对象缓存：未命中或空值标记返回 null */
    public <R> R get(String key, Class<R> type) {
        String json = redis.opsForValue().get(key);
        return (json == null || json.isEmpty()) ? null : read(json, type);
    }

    /** 读列表缓存：未命中返回 null（命中空列表返回空 List，两者语义不同） */
    public <R> List<R> getList(String key, Class<R> elementType) {
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    /** 按前缀批量删：SCAN 游标收集后统一 DEL，不用 KEYS（会阻塞 Redis 单线程） */
    public void deleteByPrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        try (var cursor = redis.scan(ScanOptions.scanOptions()
                .match(prefix + "*").count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    // ==================== 锁与序列化 ====================

    /** SETNX 抢锁，成功返回锁值（解锁时比对用），失败返回 null */
    private String tryLock(String key, long ttlSeconds) {
        String token = UUID.randomUUID().toString();
        boolean ok = Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(key, token, ttlSeconds, TimeUnit.SECONDS));
        return ok ? token : null;
    }

    /** 只删自己持有的锁：Lua 里 GET 比对一致才 DEL，避免删掉已易主的锁 */
    private void unlock(String key, String token) {
        redis.execute(UNLOCK_SCRIPT, List.of(key), token);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("缓存序列化失败", e);
        }
    }

    private <R> R read(String json, Class<R> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    private <R> R parseLogical(String json, Class<R> type) {
        RedisData wrapper = read(json, RedisData.class);
        return objectMapper.convertValue(wrapper.getData(), type);
    }

    /** 读缓存，未命中或空值标记都返回 null */
    private <R> R readCache(String key, Class<R> type) {
        String json = redis.opsForValue().get(key);
        return (json == null || json.isEmpty()) ? null : read(json, type);
    }

    private boolean isNullMarked(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /** 空值标记写入：DB 也没有时的短 TTL 空串，挡住穿透 */
    private void writeNullMark(String key) {
        redis.opsForValue().set(key, RedisConstants.CACHE_NULL_VALUE,
                RedisConstants.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /** 重建失败后用旧值把逻辑过期时间往后推 60s，防止下个请求又触发重建打爆线程池 */
    private void backoffQuietly(String key, Object oldValue) {
        try {
            setWithLogicalExpire(key, oldValue, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("退避写入也失败了，key={}", key, e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
