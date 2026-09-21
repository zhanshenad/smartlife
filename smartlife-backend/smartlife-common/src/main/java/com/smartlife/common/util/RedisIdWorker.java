package com.smartlife.common.util;

import com.smartlife.common.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 全局唯一 ID：时间戳(31位) << 32 | 当日自增序列(32位)。
 * 高位是秒级时间戳，ID 整体趋势递增，对 MySQL 聚簇索引友好。
 * 自增计数器按天分片并带 TTL，用完自然回收，不会在 Redis 里堆积。
 */
@Component
public class RedisIdWorker {

    /** 起始时间戳 2022-01-01 00:00:00 UTC，让 31 位时间戳能用得更久 */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /** 序列号占用的位数 */
    private static final int COUNT_BITS = 32;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     */
    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        String date = now.format(DATE_FORMATTER);
        String key = keyPrefix + date;

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Redis 自增失败，无法生成全局 ID：" + key);
        }
        // 每次递增都重设 TTL 会让键永不过期，所以只在首次创建时设置
        if (count == 1L) {
            stringRedisTemplate.expire(key, RedisConstants.ID_WORKER_TTL_DAYS, TimeUnit.DAYS);
        }

        return timestamp << COUNT_BITS | count;
    }
}
