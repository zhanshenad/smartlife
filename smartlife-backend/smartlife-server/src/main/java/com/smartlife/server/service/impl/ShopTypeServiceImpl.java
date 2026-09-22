package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.pojo.entity.ShopType;
import com.smartlife.server.mapper.ShopTypeMapper;
import com.smartlife.server.service.IShopTypeService;
import com.smartlife.server.util.CacheClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分类是典型冷数据（一年改不了几次），用互斥锁方案防击穿就够，
 * 不值得为它上线程池（§5.3.1 的方案分配）。
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final CacheClient cacheClient;

    public ShopTypeServiceImpl(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
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
}
