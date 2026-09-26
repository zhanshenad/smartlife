package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.entity.ShopType;
import com.smartlife.server.mapper.ShopTypeMapper;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IShopTypeService;
import com.smartlife.server.util.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分类是典型冷数据（一年改不了几次），用互斥锁方案防击穿就够，
 * 不值得为它上线程池（§5.3.1 的方案分配）。
 * 管理端增删改后删全量缓存，下次查询走互斥锁重建。
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final CacheClient cacheClient;
    private final IShopService shopService;
    private final StringRedisTemplate redis;

    public ShopTypeServiceImpl(CacheClient cacheClient, IShopService shopService,
                               StringRedisTemplate redis) {
        this.cacheClient = cacheClient;
        this.shopService = shopService;
        this.redis = redis;
    }

    @Override
    public List<ShopType> listSorted() {
        // 全量列表一个 key。泛型方法只能收 Class，List 用数组类型表达
        ShopType[] arr = cacheClient.queryWithMutex(
                RedisConstants.CACHE_SHOP_TYPE_KEY, RedisConstants.LOCK_SHOP_KEY, "list", ShopType[].class,
                k -> lambdaQuery().orderByAsc(ShopType::getSort).list().toArray(ShopType[]::new),
                RedisConstants.CACHE_SHOP_TYPE_TTL_MINUTES, TimeUnit.MINUTES);
        return arr == null ? List.of() : List.of(arr);
    }

    @Override
    public void saveType(ShopType shopType) {
        shopType.setId(null);
        save(shopType);
        evictCache();
    }

    @Override
    public void updateType(ShopType shopType) {
        if (getById(shopType.getId()) == null) {
            throw new BusinessException("店铺类型不存在");
        }
        updateById(shopType);
        evictCache();
    }

    @Override
    public void deleteType(Long id) {
        Long shops = shopService.lambdaQuery().eq(Shop::getTypeId, id).count();
        if (shops != null && shops > 0) {
            throw new BusinessException("仍有 " + shops + " 家店铺使用该类型，不能删除");
        }
        removeById(id);
        evictCache();
    }

    private void evictCache() {
        // listSorted 走 queryWithMutex(keyPrefix, ..., id="list")，真实 key 带 list 后缀
        redis.delete(RedisConstants.CACHE_SHOP_TYPE_KEY + "list");
    }
}
