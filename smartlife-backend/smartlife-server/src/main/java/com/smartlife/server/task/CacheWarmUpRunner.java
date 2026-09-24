package com.smartlife.server.task;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.pojo.entity.SeckillVoucher;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.server.mapper.SeckillVoucherMapper;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.util.CacheClient;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 启动缓存预热（§4.4）：店铺详情灌逻辑过期缓存（保证 key 常在，
 * 逻辑过期方案的前提），坐标灌 GEO，进行中的秒杀券库存灌 Redis（§5.2.6 设计点 7）。
 * Redisson 锁防多实例重复执行。
 */
@Component
public class CacheWarmUpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmUpRunner.class);

    private final IShopService shopService;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final CacheClient cacheClient;
    private final StringRedisTemplate redis;
    private final RedissonClient redissonClient;

    public CacheWarmUpRunner(IShopService shopService, SeckillVoucherMapper seckillVoucherMapper,
                             CacheClient cacheClient, StringRedisTemplate redis,
                             RedissonClient redissonClient) {
        this.shopService = shopService;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.cacheClient = cacheClient;
        this.redis = redis;
        this.redissonClient = redissonClient;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        RLock lock = redissonClient.getLock(RedisConstants.WARMUP_LOCK_KEY);
        // 不传 leaseTime 才有看门狗续期：预热超过固定时长也不会锁先失效
        boolean locked = lock.tryLock(0, TimeUnit.SECONDS);
        if (!locked) {
            log.info("别的实例正在预热，跳过");
            return;
        }
        try {
            warmUpShopDetail();
            warmUpGeo();
            warmUpSeckillStock();
        } finally {
            // 锁已极端超时易主时 unlock 会抛异常，不该中断启动
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.warn("预热锁已易主，跳过解锁");
            }
        }
    }

    private void warmUpShopDetail() {
        List<Shop> shops = shopService.lambdaQuery().eq(Shop::getStatus, 1).list();
        for (Shop shop : shops) {
            cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shop,
                    RedisConstants.CACHE_SHOP_TTL_MINUTES, TimeUnit.MINUTES);
        }
        log.info("店铺详情预热完成：{} 家", shops.size());
    }

    private void warmUpGeo() {
        List<Shop> shops = shopService.list();
        Map<Long, List<Shop>> byType = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        byType.forEach((typeId, list) -> {
            String geoKey = RedisConstants.SHOP_GEO_KEY + typeId;
            // 先清再灌：改过类型的店会在旧 key 里残留成员
            redis.delete(geoKey);
            List<RedisGeoCommands.GeoLocation<String>> locations = list.stream()
                    .map(s -> new RedisGeoCommands.GeoLocation<>(String.valueOf(s.getId()),
                            new Point(s.getX(), s.getY())))
                    .toList();
            redis.opsForGeo().add(geoKey, locations);
        });
        log.info("GEO 预热完成：{} 类 {} 家", byType.size(), shops.size());
    }

    /** 秒杀库存预热：只灌时间窗覆盖当下的券（还没开始的按 DB 原值灌） */
    private void warmUpSeckillStock() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillVoucher> seckills = seckillVoucherMapper.selectList(null);
        int count = 0;
        for (SeckillVoucher sv : seckills) {
            if (!sv.getEndTime().isAfter(now)) {
                continue;
            }
            redis.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + sv.getVoucherId(),
                    String.valueOf(sv.getStock()));
            count++;
        }
        log.info("秒杀库存预热完成：{}/{} 张进行中或未开始", count, seckills.size());
    }
}
