package com.smartlife.server.mq;

import com.smartlife.common.constant.RedisConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 名额回补的 Redis 副作用。锁住"脚本必须声明返回值类型"——漏了 setResultType 时
 * Redis 端执行成功、客户端却抛异常，只看状态会误判为通过。
 */
@SpringBootTest
@DisplayName("秒杀名额回补")
class SeckillSlotRefundTest {

    private static final Long VOUCHER_ID = 900001L;
    private static final Long USER_ID = 48L;

    @Autowired
    private SeckillSlotRefund seckillSlotRefund;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(RedisConstants.SECKILL_STOCK_KEY + VOUCHER_ID);
        redis.delete(RedisConstants.SECKILL_ORDER_KEY + VOUCHER_ID);
    }

    @Test
    @DisplayName("库存 +1、用户被摘除，脚本返回值可正常解析")
    void refundRestoresStockAndRemovesUser() {
        redis.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + VOUCHER_ID, "5");
        redis.opsForSet().add(RedisConstants.SECKILL_ORDER_KEY + VOUCHER_ID, USER_ID.toString());

        seckillSlotRefund.refund(VOUCHER_ID, USER_ID);

        assertEquals("6", redis.opsForValue().get(RedisConstants.SECKILL_STOCK_KEY + VOUCHER_ID));
        assertFalse(Boolean.TRUE.equals(redis.opsForSet()
                .isMember(RedisConstants.SECKILL_ORDER_KEY + VOUCHER_ID, USER_ID.toString())));
    }
}
