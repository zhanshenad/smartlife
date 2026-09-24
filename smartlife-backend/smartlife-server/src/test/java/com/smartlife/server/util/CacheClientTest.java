package com.smartlife.server.util;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.pojo.entity.Shop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存三件套防护的行为验证：穿透写空值、雪崩随机 TTL、
 * 互斥锁并发只放一个进库、逻辑过期旧值返回 + 异步重建 + miss 自愈。
 */
@SpringBootTest
@DisplayName("缓存三件套防护")
class CacheClientTest {

    private static final String KEY_PREFIX = "cache:test:";

    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        List.of(1, 2, 3, 4, 5).forEach(i -> redis.delete(KEY_PREFIX + i));
        List.of(1, 2, 3, 4, 5).forEach(i -> {
            redis.delete(RedisConstants.LOCK_SHOP_KEY + i);
            redis.delete(RedisConstants.LOCK_REBUILD_KEY + i);
        });
    }

    @Test
    @DisplayName("穿透：DB 也没有时写空值缓存，后续请求不再打 DB")
    void passThroughWritesNullMark() {
        AtomicInteger dbCalls = new AtomicInteger();
        Function<Long, Shop> db = id -> {
            dbCalls.incrementAndGet();
            return null;
        };

        assertNull(cacheClient.queryWithPassThrough(KEY_PREFIX, 1L, Shop.class, db, 5, TimeUnit.MINUTES));
        assertEquals(1, dbCalls.get());

        // 第二次命中空值缓存，不涨
        assertNull(cacheClient.queryWithPassThrough(KEY_PREFIX, 1L, Shop.class, db, 5, TimeUnit.MINUTES));
        assertEquals(1, dbCalls.get());
    }

    @Test
    @DisplayName("雪崩：写入 TTL 带 [0, base/3) 随机增量")
    void setAppliesRandomTtl() {
        cacheClient.set(KEY_PREFIX + 2, new Shop(), 30, TimeUnit.SECONDS);
        Long ttl = redis.getExpire(KEY_PREFIX + 2, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl >= 29 && ttl <= 41, "TTL 应在 30s 基础 + 0~10s 抖动内，实际=" + ttl);
    }

    @Test
    @DisplayName("互斥锁：8 线程同时 miss，只放 1 个进库重建")
    void mutexLetsOnlyOneThreadRebuild() throws Exception {
        AtomicInteger dbCalls = new AtomicInteger();
        Function<String, Shop> db = k -> {
            dbCalls.incrementAndGet();
            sleepQuietly(200); // 拉长重建窗口制造竞争
            Shop s = new Shop();
            s.setName("库里的店");
            return s;
        };

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Shop>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return cacheClient.queryWithMutex(KEY_PREFIX, RedisConstants.LOCK_SHOP_KEY,
                            "3", Shop.class, db, 5, TimeUnit.MINUTES);
                }));
            }
            start.countDown();
            for (Future<Shop> f : futures) {
                assertNotNull(f.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, dbCalls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("逻辑过期：立即返回旧值，后台异步重建出新值")
    void logicalExpireReturnsStaleThenRebuilds() {
        Shop old = new Shop();
        old.setName("旧值");
        // 负 TTL = 写进去就已逻辑过期
        cacheClient.setWithLogicalExpire(KEY_PREFIX + 4, old, -1, TimeUnit.SECONDS);

        AtomicInteger dbCalls = new AtomicInteger();
        Function<Long, Shop> db = id -> {
            dbCalls.incrementAndGet();
            Shop s = new Shop();
            s.setName("新值");
            return s;
        };

        Shop r = cacheClient.queryWithLogicalExpire(KEY_PREFIX, 4L, Shop.class, db, 5, TimeUnit.MINUTES);
        assertEquals("旧值", r.getName(), "逻辑过期瞬间应返回旧值而非阻塞等待");
        assertEquals(0, dbCalls.get(), "重建是异步的，同步链路不应查库");

        // 轮询等异步重建落库缓存（最多 3s）
        Shop after = null;
        for (int i = 0; i < 30; i++) {
            sleepQuietly(100);
            after = cacheClient.queryWithLogicalExpire(KEY_PREFIX, 4L, Shop.class, db, 5, TimeUnit.MINUTES);
            if ("新值".equals(after.getName())) {
                break;
            }
        }
        assertEquals("新值", Objects.requireNonNull(after, "3 秒内未完成异步重建").getName(),
                "异步重建完成后应读到新值");
    }

    @Test
    @DisplayName("逻辑过期 miss 自愈：key 被清后同步查库回填")
    void logicalExpireSelfHealsOnMiss() {
        Shop dbShop = new Shop();
        dbShop.setName("DB值");

        Shop r = cacheClient.queryWithLogicalExpire(KEY_PREFIX, 5L, Shop.class,
                id -> dbShop, 5, TimeUnit.MINUTES);
        assertEquals("DB值", r.getName());
        assertTrue(Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + 5)), "miss 重建后 key 应存在");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
