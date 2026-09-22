package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.ShopDTO;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.entity.ShopType;
import com.smartlife.pojo.vo.ShopVO;
import com.smartlife.server.util.CacheClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺查询与商家端更新。DB 改动由 @Transactional 回滚；
 * Redis 侧（缓存命中/删除）不受事务管，断言后手动清。
 */
@SpringBootTest
@Transactional
@DisplayName("店铺：缓存查询与商家更新")
class ShopServiceImplTest {

    /** 测试商家的真实 id（13800000002，拥有 1 号店） */
    private static final Long MERCHANT_ID = 47L;
    private static final Long SHOP_ID = 1L;

    @Autowired
    private IShopService shopService;
    @Autowired
    private IShopTypeService shopTypeService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        redis.delete(RedisConstants.CACHE_SHOP_KEY + SHOP_ID);
        redis.delete(RedisConstants.CACHE_SHOP_KEY + 999);
    }

    @Test
    @DisplayName("详情命中缓存：DB 无此 id 也能返回缓存值")
    void queryByIdHitsCache() {
        Shop cached = new Shop();
        cached.setId(999L);
        cached.setName("缓存里的店");
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 999, cached,
                5, TimeUnit.MINUTES);

        Shop r = shopService.queryById(999L);
        assertEquals("缓存里的店", r.getName());
    }

    @Test
    @DisplayName("分类列表可查（互斥锁路径）")
    void listShopTypes() {
        List<ShopType> types = shopTypeService.listSorted();
        assertFalse(types.isEmpty());
    }

    @Test
    @DisplayName("按类型分页：美食 10 家")
    void queryOfTypePages() {
        PageResult<Shop> page = shopService.queryOfType(1L, 1, 5);
        assertEquals(10, page.getTotal());
        assertEquals(5, page.getRecords().size());
    }

    @Test
    @DisplayName("附近店铺：GEO 命中并按距离升序")
    void queryNearbySortedByDistance() {
        List<ShopVO> nearby = shopService.queryNearby(123.4227, 41.7995, 1L, 1, 10);
        assertFalse(nearby.isEmpty(), "东大坐标 5km 内应有美食店（依赖启动预热）");
        for (int i = 1; i < nearby.size(); i++) {
            assertTrue(nearby.get(i - 1).getDistance() <= nearby.get(i).getDistance(),
                    "距离应升序排列");
        }
        assertTrue(nearby.get(0).getDistance() < 1000, "最近一家应在 1km 内");
    }

    @Test
    @DisplayName("更新归属校验：非店主商家被拒，缓存保留")
    void updateRejectsNonOwner() {
        givenCachedShop();
        BaseContext.set(new LoginUser(999L, 2, "别人家的商家"));

        ShopDTO dto = validDto();
        assertThrows(BusinessException.class, () -> shopService.updateShop(dto));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(RedisConstants.CACHE_SHOP_KEY + SHOP_ID)),
                "被拒时不应动缓存");
    }

    @Test
    @DisplayName("更新成功：店主改库后删缓存（Cache Aside）")
    void updateDeletesCache() {
        givenCachedShop();
        BaseContext.set(new LoginUser(MERCHANT_ID, 2, "测试商家"));

        shopService.updateShop(validDto());

        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.CACHE_SHOP_KEY + SHOP_ID)),
                "改库后应删缓存");
        assertEquals("改名后的店", shopService.getById(SHOP_ID).getName());
    }

    private void givenCachedShop() {
        Shop cached = new Shop();
        cached.setId(SHOP_ID);
        cached.setName("缓存里的旧店名");
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + SHOP_ID, cached,
                5, TimeUnit.MINUTES);
    }

    private ShopDTO validDto() {
        ShopDTO dto = new ShopDTO();
        dto.setId(SHOP_ID);
        dto.setName("改名后的店");
        dto.setTypeId(1L);
        dto.setAddress("文化路3号巷内");
        dto.setX(123.4232);
        dto.setY(41.7999);
        return dto;
    }
}
