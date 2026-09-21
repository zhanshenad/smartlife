package com.smartlife.server.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端。用途很窄：只给需要看门狗自动续期的长任务锁用（如缓存预热）。
 * 普通的缓存击穿互斥锁用 StringRedisTemplate 的 SETNX + TTL 就够，不必动用 Redisson。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    /** destroyMethod 保证应用关闭时释放 Netty 线程池与连接 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                // 连接池按需给，学习项目并发不大
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16);
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
