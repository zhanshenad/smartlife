package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.DishDTO;
import com.smartlife.pojo.entity.Category;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.DishFlavor;
import com.smartlife.pojo.entity.SetmealDish;
import com.smartlife.pojo.vo.DishVO;
import com.smartlife.server.mapper.CategoryMapper;
import com.smartlife.server.mapper.DishFlavorMapper;
import com.smartlife.server.mapper.DishMapper;
import com.smartlife.server.mapper.SetmealDishMapper;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.util.CacheClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 菜品管理（商家端）。写操作一律归属校验 + Cache Aside：
 * 改库后按店前缀清菜品缓存（key 是 cache:dish:{shopId}:{categoryId}，
 * 改动可能影响任意分类的列表，索性整店清，量级一次 SCAN 几个 key）。
 */
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements IDishService {

    private final DishFlavorMapper flavorMapper;
    private final SetmealDishMapper setmealDishMapper;
    private final CategoryMapper categoryMapper;
    private final IShopService shopService;
    private final CacheClient cacheClient;

    public DishServiceImpl(DishFlavorMapper flavorMapper, SetmealDishMapper setmealDishMapper,
                           CategoryMapper categoryMapper, IShopService shopService, CacheClient cacheClient) {
        this.flavorMapper = flavorMapper;
        this.setmealDishMapper = setmealDishMapper;
        this.categoryMapper = categoryMapper;
        this.shopService = shopService;
        this.cacheClient = cacheClient;
    }

    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dto) {
        Long shopId = shopService.requireMyShopId();
        Dish dish = new Dish();
        BeanUtils.copyProperties(dto, dish);
        dish.setId(null);
        dish.setShopId(shopId);
        // 新增默认停售：配好图和口味再手动起售
        dish.setStatus(StatusConstants.Common.DISABLED);
        save(dish);
        saveFlavors(dto.getFlavors(), dish.getId());
        evictDishCache(shopId);
    }

    @Override
    public PageResult<DishVO> pageQuery(Long categoryId, Integer status, String name, long current, long size) {
        Long shopId = shopService.requireMyShopId();
        Page<Dish> page = lambdaQuery()
                .eq(Dish::getShopId, shopId)
                .eq(categoryId != null, Dish::getCategoryId, categoryId)
                .eq(status != null, Dish::getStatus, status)
                .like(name != null && !name.isBlank(), Dish::getName, name)
                .orderByDesc(Dish::getUpdateTime)
                .page(new Page<>(current, size));

        Set<Long> catIds = page.getRecords().stream().map(Dish::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> catNames = catIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(catIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));
        List<DishVO> vos = page.getRecords().stream().map(d -> {
            DishVO vo = new DishVO();
            BeanUtils.copyProperties(d, vo);
            vo.setCategoryName(catNames.get(d.getCategoryId()));
            return vo;
        }).toList();
        return PageResult.of(page.getTotal(), vos);
    }

    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = requireOwnedDish(id);
        DishVO vo = new DishVO();
        BeanUtils.copyProperties(dish, vo);
        vo.setFlavors(flavorMapper.selectList(
                new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, id)));
        return vo;
    }

    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dto) {
        Dish db = requireOwnedDish(dto.getId());
        Dish dish = new Dish();
        BeanUtils.copyProperties(dto, dish);
        // shopId/status 不随修改漂移：前者钉死归属，后者 null 字段 MP 不会更新
        dish.setShopId(db.getShopId());
        updateById(dish);
        // 口味整体删旧插新，一两条规定不做 diff
        flavorMapper.delete(new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, dto.getId()));
        saveFlavors(dto.getFlavors(), dto.getId());
        evictDishCache(db.getShopId());
    }

    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {
        Long shopId = shopService.requireMyShopId();
        List<Dish> dishes = listByIds(ids);
        if (dishes.size() != ids.size()) {
            throw new BusinessException("有菜品不存在");
        }
        for (Dish d : dishes) {
            if (!d.getShopId().equals(shopId)) {
                throw new BusinessException("只能操作自己店铺的菜品");
            }
            if (d.getStatus() != null && d.getStatus() == StatusConstants.Common.ENABLED) {
                throw new BusinessException("起售中的菜品不能删除：" + d.getName());
            }
        }
        Long bound = setmealDishMapper.selectCount(
                new LambdaQueryWrapper<SetmealDish>().in(SetmealDish::getDishId, ids));
        if (bound != null && bound > 0) {
            throw new BusinessException("有菜品被套餐关联，请先解除关联");
        }
        removeByIds(ids);
        flavorMapper.delete(new LambdaQueryWrapper<DishFlavor>().in(DishFlavor::getDishId, ids));
        evictDishCache(shopId);
    }

    @Override
    public void startStop(Long id, Integer status) {
        if (status == null || (status != StatusConstants.Common.DISABLED
                && status != StatusConstants.Common.ENABLED)) {
            throw new BusinessException("非法的起售状态");
        }
        Dish db = requireOwnedDish(id);
        lambdaUpdate().eq(Dish::getId, id).set(Dish::getStatus, status).update();
        evictDishCache(db.getShopId());
    }

    @Override
    public List<DishVO> listOnSale(Long shopId, Long categoryId) {
        String key = RedisConstants.CACHE_DISH_KEY + shopId + ":" + categoryId;
        List<DishVO> cached = cacheClient.getList(key, DishVO.class);
        if (cached != null) {
            return cached;
        }
        List<Dish> dishes = lambdaQuery()
                .eq(Dish::getShopId, shopId)
                .eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getStatus, StatusConstants.Common.ENABLED)
                .orderByDesc(Dish::getUpdateTime)
                .list();
        Map<Long, List<DishFlavor>> flavorsByDish = loadFlavors(
                dishes.stream().map(Dish::getId).toList());
        List<DishVO> vos = dishes.stream().map(d -> {
            DishVO vo = new DishVO();
            BeanUtils.copyProperties(d, vo);
            vo.setFlavors(flavorsByDish.getOrDefault(d.getId(), List.of()));
            return vo;
        }).toList();
        // 空列表也缓存：新店新分类的空货架不该每次都打 DB
        cacheClient.set(key, vos, RedisConstants.CACHE_DISH_TTL_MINUTES, TimeUnit.MINUTES);
        return vos;
    }

    private Map<Long, List<DishFlavor>> loadFlavors(List<Long> dishIds) {
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return flavorMapper.selectList(
                        new LambdaQueryWrapper<DishFlavor>().in(DishFlavor::getDishId, dishIds))
                .stream().collect(Collectors.groupingBy(DishFlavor::getDishId));
    }

    /** 查菜品并校验归属当前商家的店 */
    private Dish requireOwnedDish(Long id) {
        Long shopId = shopService.requireMyShopId();
        Dish dish = getById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        if (!dish.getShopId().equals(shopId)) {
            throw new BusinessException("只能操作自己店铺的菜品");
        }
        return dish;
    }

    private void saveFlavors(List<DishFlavor> flavors, Long dishId) {
        if (flavors == null || flavors.isEmpty()) {
            return;
        }
        for (DishFlavor f : flavors) {
            f.setId(null);
            f.setDishId(dishId);
            flavorMapper.insert(f);
        }
    }

    private void evictDishCache(Long shopId) {
        cacheClient.deleteByPrefix(RedisConstants.CACHE_DISH_KEY + shopId + ":");
    }
}
