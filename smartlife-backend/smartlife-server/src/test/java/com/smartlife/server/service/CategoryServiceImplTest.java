package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.Category;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.ShopType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 类目字典测试：分类 CRUD 护栏 + 店铺类型缓存失效 */
@SpringBootTest
@Transactional
@DisplayName("类目字典：分类/店铺类型维护")
class CategoryServiceImplTest {

    @Autowired
    private ICategoryService categoryService;
    @Autowired
    private IShopTypeService shopTypeService;
    @Autowired
    private IDishService dishService;
    @Autowired
    private StringRedisTemplate redis;

    private Category newCategory(String name, int type) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setSort(1);
        categoryService.saveCategory(c);
        return c;
    }

    @Test
    @DisplayName("新增：同名分类拒绝，默认启用")
    void saveRejectsDuplicateName() {
        String name = "甜品饮品" + System.nanoTime() % 1000;
        newCategory(name, 1);

        Category dup = new Category();
        dup.setName(name);
        dup.setType(1);
        assertThrows(BusinessException.class, () -> categoryService.saveCategory(dup));
    }

    @Test
    @DisplayName("修改：分类类型不允许改")
    void updateRejectsTypeChange() {
        Category c = newCategory("测试分类A" + System.nanoTime() % 1000, 1);

        c.setType(2);
        BusinessException e = assertThrows(BusinessException.class,
                () -> categoryService.updateCategory(c));
        assertTrue(e.getMessage().contains("类型"));

        c.setType(1);
        c.setName("改名后");
        categoryService.updateCategory(c);
        assertEquals("改名后", categoryService.getById(c.getId()).getName());
    }

    @Test
    @DisplayName("删除：分类下有菜品拒绝")
    void deleteRejectsWhenInUse() {
        Category c = newCategory("被引用分类" + System.nanoTime() % 1000, 1);
        Dish dish = new Dish();
        dish.setShopId(1L);
        dish.setName("占位菜品");
        dish.setCategoryId(c.getId());
        dish.setPrice(1000);
        dish.setStatus(StatusConstants.Common.DISABLED);
        dishService.save(dish);

        BusinessException e = assertThrows(BusinessException.class,
                () -> categoryService.deleteCategory(c.getId()));
        assertTrue(e.getMessage().contains("不能删除"));
    }

    @Test
    @DisplayName("删除：空分类可删，启停可用")
    void deleteAndStartStop() {
        Category c = newCategory("空分类" + System.nanoTime() % 1000, 1);

        categoryService.deleteCategory(c.getId());
        assertEquals(0, categoryService.lambdaQuery()
                .eq(Category::getId, c.getId()).count());

        Category other = newCategory("启停分类" + System.nanoTime() % 1000, 1);
        categoryService.startStop(other.getId(), StatusConstants.Common.DISABLED);
        assertEquals(StatusConstants.Common.DISABLED,
                categoryService.getById(other.getId()).getStatus());
    }

    @Test
    @DisplayName("店铺类型：增删改后全量缓存失效")
    void shopTypeMutationsEvictCache() {
        // queryWithMutex 的 key = 前缀 + id，列表 id 固定为 "list"
        String cacheKey = RedisConstants.CACHE_SHOP_TYPE_KEY + "list";
        shopTypeService.listSorted();
        assertNotNull(redis.opsForValue().get(cacheKey),
                "前置：列表查询应生成缓存");

        ShopType type = new ShopType();
        type.setName("测试类型" + System.nanoTime() % 1000);
        type.setSort(99);
        shopTypeService.saveType(type);

        assertFalse(Boolean.TRUE.equals(redis.hasKey(cacheKey)),
                "写入后缓存应被删除");

        type.setName("改名");
        shopTypeService.updateType(type);
        shopTypeService.deleteType(type.getId());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(cacheKey)));
    }

    @Test
    @DisplayName("店铺类型：仍有店铺使用时删除拒绝")
    void shopTypeDeleteRejectsWhenInUse() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> shopTypeService.deleteType(1L));
        assertTrue(e.getMessage().contains("不能删除"));
    }
}
