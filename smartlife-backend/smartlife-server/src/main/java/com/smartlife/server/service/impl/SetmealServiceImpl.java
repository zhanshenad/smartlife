package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.SetmealDTO;
import com.smartlife.pojo.entity.Category;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.SetmealDish;
import com.smartlife.pojo.vo.SetmealVO;
import com.smartlife.server.mapper.CategoryMapper;
import com.smartlife.server.mapper.SetmealDishMapper;
import com.smartlife.server.mapper.SetmealMapper;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.ISetmealService;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.util.CacheClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 套餐管理（商家端）。关联菜品的 name/price 快照一律服务端回查回填，
 * 不信任前端——防止展示价被篡改。写操作后按店前缀清套餐缓存。
 */
@Service
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements ISetmealService {

    private final SetmealDishMapper setmealDishMapper;
    private final CategoryMapper categoryMapper;
    private final IDishService dishService;
    private final IShopService shopService;
    private final CacheClient cacheClient;

    public SetmealServiceImpl(SetmealDishMapper setmealDishMapper, CategoryMapper categoryMapper,
                              IDishService dishService, IShopService shopService, CacheClient cacheClient) {
        this.setmealDishMapper = setmealDishMapper;
        this.categoryMapper = categoryMapper;
        this.dishService = dishService;
        this.shopService = shopService;
        this.cacheClient = cacheClient;
    }

    @Override
    @Transactional
    public void saveWithDish(SetmealDTO dto) {
        Long shopId = shopService.requireMyShopId();
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(dto, setmeal);
        setmeal.setId(null);
        setmeal.setShopId(shopId);
        setmeal.setStatus(StatusConstants.Common.DISABLED);
        save(setmeal);
        saveDishes(dto.getSetmealDishes(), setmeal.getId(), shopId);
        evictSetmealCache(shopId);
    }

    @Override
    public PageResult<SetmealVO> pageQuery(Long categoryId, Integer status, String name, long current, long size) {
        Long shopId = shopService.requireMyShopId();
        Page<Setmeal> page = lambdaQuery()
                .eq(Setmeal::getShopId, shopId)
                .eq(categoryId != null, Setmeal::getCategoryId, categoryId)
                .eq(status != null, Setmeal::getStatus, status)
                .like(name != null && !name.isBlank(), Setmeal::getName, name)
                .orderByDesc(Setmeal::getUpdateTime)
                .page(new Page<>(current, size));

        Set<Long> catIds = page.getRecords().stream().map(Setmeal::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> catNames = catIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(catIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));
        List<SetmealVO> vos = page.getRecords().stream().map(s -> {
            SetmealVO vo = new SetmealVO();
            BeanUtils.copyProperties(s, vo);
            vo.setCategoryName(catNames.get(s.getCategoryId()));
            return vo;
        }).toList();
        return PageResult.of(page.getTotal(), vos);
    }

    @Override
    public SetmealVO getByIdWithDish(Long id) {
        Setmeal setmeal = requireOwnedSetmeal(id);
        SetmealVO vo = new SetmealVO();
        BeanUtils.copyProperties(setmeal, vo);
        vo.setSetmealDishes(setmealDishMapper.selectList(
                new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, id)));
        return vo;
    }

    @Override
    @Transactional
    public void updateWithDish(SetmealDTO dto) {
        Setmeal db = requireOwnedSetmeal(dto.getId());
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(dto, setmeal);
        setmeal.setShopId(db.getShopId());
        updateById(setmeal);
        setmealDishMapper.delete(
                new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, dto.getId()));
        saveDishes(dto.getSetmealDishes(), dto.getId(), db.getShopId());
        evictSetmealCache(db.getShopId());
    }

    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {
        Long shopId = shopService.requireMyShopId();
        List<Setmeal> setmeals = listByIds(ids);
        if (setmeals.size() != ids.size()) {
            throw new BusinessException("有套餐不存在");
        }
        for (Setmeal s : setmeals) {
            if (!s.getShopId().equals(shopId)) {
                throw new BusinessException("只能操作自己店铺的套餐");
            }
            if (s.getStatus() != null && s.getStatus() == StatusConstants.Common.ENABLED) {
                throw new BusinessException("起售中的套餐不能删除：" + s.getName());
            }
        }
        removeByIds(ids);
        setmealDishMapper.delete(new LambdaQueryWrapper<SetmealDish>().in(SetmealDish::getSetmealId, ids));
        evictSetmealCache(shopId);
    }

    @Override
    public void startStop(Long id, Integer status) {
        if (status == null || (status != StatusConstants.Common.DISABLED
                && status != StatusConstants.Common.ENABLED)) {
            throw new BusinessException("非法的起售状态");
        }
        Setmeal db = requireOwnedSetmeal(id);
        if (status == 1) {
            // 套餐含停售菜品时不允许起售，用户端会点到下架单品
            List<SetmealDish> sds = setmealDishMapper.selectList(
                    new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, id));
            List<Long> dishIds = sds.stream().map(SetmealDish::getDishId).toList();
            if (!dishIds.isEmpty()) {
                boolean hasStopped = dishService.listByIds(dishIds).stream()
                        .anyMatch(d -> d.getStatus() == null
                                || d.getStatus() != StatusConstants.Common.ENABLED);
                if (hasStopped) {
                    throw new BusinessException("套餐内含停售菜品，无法起售");
                }
            }
        }
        lambdaUpdate().eq(Setmeal::getId, id).set(Setmeal::getStatus, status).update();
        evictSetmealCache(db.getShopId());
    }

    @Override
    public List<SetmealVO> listOnSale(Long shopId, Long categoryId) {
        String key = RedisConstants.CACHE_SETMEAL_KEY + shopId + ":" + categoryId;
        List<SetmealVO> cached = cacheClient.getList(key, SetmealVO.class);
        if (cached != null) {
            return cached;
        }
        List<Setmeal> setmeals = lambdaQuery()
                .eq(Setmeal::getShopId, shopId)
                .eq(Setmeal::getCategoryId, categoryId)
                .eq(Setmeal::getStatus, StatusConstants.Common.ENABLED)
                .orderByDesc(Setmeal::getUpdateTime)
                .list();
        List<SetmealVO> vos = setmeals.stream().map(s -> {
            SetmealVO vo = new SetmealVO();
            BeanUtils.copyProperties(s, vo);
            return vo;
        }).toList();
        cacheClient.set(key, vos, RedisConstants.CACHE_SETMEAL_TTL_MINUTES, TimeUnit.MINUTES);
        return vos;
    }

    private Setmeal requireOwnedSetmeal(Long id) {
        Long shopId = shopService.requireMyShopId();
        Setmeal setmeal = getById(id);
        if (setmeal == null) {
            throw new BusinessException("套餐不存在");
        }
        if (!setmeal.getShopId().equals(shopId)) {
            throw new BusinessException("只能操作自己店铺的套餐");
        }
        return setmeal;
    }

    /** 关联菜品落库：校验本店 + 服务端回填 name/price 快照 */
    private void saveDishes(List<SetmealDish> dishes, Long setmealId, Long shopId) {
        List<Long> dishIds = dishes.stream().map(SetmealDish::getDishId).toList();
        Map<Long, Dish> dishMap = dishService.listByIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        for (SetmealDish sd : dishes) {
            Dish d = dishMap.get(sd.getDishId());
            if (d == null) {
                throw new BusinessException("套餐内菜品不存在");
            }
            if (!d.getShopId().equals(shopId)) {
                throw new BusinessException("套餐只能关联本店菜品");
            }
            sd.setId(null);
            sd.setSetmealId(setmealId);
            sd.setName(d.getName());
            sd.setPrice(d.getPrice());
            setmealDishMapper.insert(sd);
        }
    }

    private void evictSetmealCache(Long shopId) {
        cacheClient.deleteByPrefix(RedisConstants.CACHE_SETMEAL_KEY + shopId + ":");
    }
}
