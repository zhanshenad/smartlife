package com.smartlife.server.mq;

import com.smartlife.common.constant.RedisConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀名额回补：把 Lua 预扣的库存还回去、把占位的用户摘掉，两步在一个脚本里原子完成。
 * 消息确认失败时调用——消息没进队列就不会产生订单，名额必须释放，否则用户被 SISMEMBER 卡死。
 */
@Component
public class SeckillSlotRefund {

    private static final DefaultRedisScript<Long> REFUND_SCRIPT;

    static {
        REFUND_SCRIPT = new DefaultRedisScript<>();
        REFUND_SCRIPT.setLocation(new ClassPathResource("lua/seckill-refund.lua"));
        // 必须声明返回值类型：脚本返回整数，不声明会按 StatusOutput 解析，
        // 客户端抛 UnsupportedOperationException（脚本其实已在 Redis 端执行成功）
        REFUND_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public SeckillSlotRefund(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void refund(Long voucherId, Long userId) {
        redis.execute(REFUND_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                        RedisConstants.SECKILL_ORDER_KEY + voucherId),
                userId.toString());
    }
}
