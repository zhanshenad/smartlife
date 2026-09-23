package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.ShopDTO;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.vo.ShopVO;
import com.smartlife.server.mapper.ShopMapper;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.util.CacheClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 店铺查询与商家端维护。方案分配（§5.3.1）：详情走逻辑过期（热点、零阻塞），
 * 分类走互斥锁，分页列表不缓存。
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /** 附近搜索半径 */
    private static final double NEARBY_RADIUS_METERS = 5000;

    private final CacheClient cacheClient;
    private final StringRedisTemplate redis;

    public ShopServiceImpl(CacheClient cacheClient, StringRedisTemplate redis) {
        this.cacheClient = cacheClient;
        this.redis = redis;
    }

    @Override
    public Shop queryById(Long id) {
        // 热点详情：key 由预热保证常在，miss（flushdb/误删）时方法内部同步自愈
        return cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public PageResult<Shop> queryOfType(Long typeId, long current, long size) {
        // 分页列表不缓存：翻页组合爆炸、命中率低，只查营业中的
        Page<Shop> page = lambdaQuery()
                .eq(Shop::getTypeId, typeId)
                .eq(Shop::getStatus, 1)
                .orderByDesc(Shop::getSold)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public List<ShopVO> queryNearby(Double x, Double y, Long typeId, long current, long size) {
        // GEO 按 typeId 分 key：GEOSEARCH 无法按类型过滤，只能 key 级隔离
        String geoKey = RedisConstants.SHOP_GEO_KEY + typeId;
        int start = (int) ((current - 1) * size);
        // GEO 的 limit 只有"取前 N 条"，分页取前 start+size 条再截掉 start
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                geoKey,
                GeoReference.fromCoordinate(x, y),
                new Distance(NEARBY_RADIUS_METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(start + (int) size));
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }
        var content = results.getContent();
        List<Long> ids = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (int i = start; i < Math.min(start + (int) size, content.size()); i++) {
            var item = content.get(i);
            ids.add(Long.valueOf(item.getContent().getName()));
            distances.add(item.getDistance().getValue());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        // 批量查库填业务字段，保持 GEO 返回的距离顺序
        Map<Long, Shop> shopMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity()));
        List<ShopVO> vos = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Shop shop = shopMap.get(ids.get(i));
            if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
                continue;
            }
            ShopVO vo = new ShopVO();
            BeanUtils.copyProperties(shop, vo);
            vo.setDistance(distances.get(i));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public void updateShop(ShopDTO dto) {
        // 归属校验：商家只能改自己的店，管理员可改任何店
        LoginUser user = BaseContext.require();
        Shop db = getById(dto.getId());
        if (db == null) {
            throw new BusinessException("店铺不存在");
        }
        if (user.isMerchant() && !user.getId().equals(db.getMerchantId())) {
            throw new BusinessException("只能操作自己的店铺");
        }
        Shop shop = new Shop();
        BeanUtils.copyProperties(dto, shop);
        updateById(shop);
        // Cache Aside：先改库后删缓存，下次查询自然重建。
        // 顺序反过来的话，"先删缓存→改库期间并发读把旧值又写回"会造成长期脏数据
        cacheClient.delete(RedisConstants.CACHE_SHOP_KEY + dto.getId());
    }

    @Override
    public Long requireMyShopId() {
        Long merchantId = BaseContext.require().getId();
        return lambdaQuery().eq(Shop::getMerchantId, merchantId)
                .oneOpt().map(Shop::getId)
                .orElseThrow(() -> new BusinessException("你还没有店铺"));
    }
}
