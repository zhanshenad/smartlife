package com.smartlife.server.service.impl;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.server.service.IStatsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * UV 统计（HyperLogLog）：PFADD 记录 / PFCOUNT 查询，按店铺按天分 key。
 * HLL 是去重计数不是名单，只答"多少人"不答"是谁"。
 */
@Service
public class StatsServiceImpl implements IStatsService {

    private final StringRedisTemplate redis;

    public StatsServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void recordShopVisit(Long shopId, Long userId) {
        if (userId == null) {
            return;
        }
        redis.opsForHyperLogLog().add(RedisConstants.UV_SHOP_KEY + shopId
                + ":" + LocalDate.now(), userId.toString());
    }

    @Override
    public long shopUv(Long shopId, String date) {
        LocalDate day;
        try {
            day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
        Long count = redis.opsForHyperLogLog()
                .size(RedisConstants.UV_SHOP_KEY + shopId + ":" + day);
        return count == null ? 0 : count;
    }
}
