package com.smartlife.server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UV：HyperLogLog 去重计数与小规模误差演示。
 */
@SpringBootTest
@DisplayName("UV统计：HyperLogLog")
class StatsServiceImplTest {

    private static final Long SHOP_ID = 1L;
    private static final String KEY =
            "uv:shop:" + SHOP_ID + ":" + LocalDate.now();

    @Autowired
    private IStatsService statsService;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanBefore() {
        // 真机验收与测试共用同库同 key，先清防污染断言
        redis.delete(KEY);
    }

    @AfterEach
    void cleanUp() {
        redis.delete(KEY);
    }

    @Test
    @DisplayName("去重：同一用户多次访问只计一次")
    void recordShopVisitDedupes() {
        statsService.recordShopVisit(SHOP_ID, 48L);
        statsService.recordShopVisit(SHOP_ID, 48L);
        statsService.recordShopVisit(SHOP_ID, 49L);

        assertEquals(2, statsService.shopUv(SHOP_ID, null));
        assertEquals(0, statsService.shopUv(SHOP_ID, "2000-01-01"), "无数据的日期为 0");
    }

    @Test
    @DisplayName("误差：千级用户 PFCOUNT 误差 < 1%")
    void uvApproximates() {
        for (long uid = 1; uid <= 1000; uid++) {
            statsService.recordShopVisit(SHOP_ID, uid);
        }

        long uv = statsService.shopUv(SHOP_ID, null);
        assertTrue(Math.abs(uv - 1000) <= 10,
                "实际 UV 允许 0.81% 理论误差，实测：" + uv);
    }
}
