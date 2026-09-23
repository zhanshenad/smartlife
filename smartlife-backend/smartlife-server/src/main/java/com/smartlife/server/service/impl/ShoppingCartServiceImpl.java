package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.ShoppingCart;
import com.smartlife.server.mapper.ShoppingCartMapper;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.ISetmealService;
import com.smartlife.server.service.IShoppingCartService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车（用户端，纯 MySQL 不缓存，§6.2-7 定案）。
 * 商品快照（名称/图/单价）服务端回查，加购与减购都是一条 SQL 的 DB 端原子操作。
 */
@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart>
        implements IShoppingCartService {

    private final IDishService dishService;
    private final ISetmealService setmealService;

    public ShoppingCartServiceImpl(IDishService dishService, ISetmealService setmealService) {
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    @Override
    public void add(ShoppingCartDTO dto) {
        if (dto.getDishId() == null && dto.getSetmealId() == null) {
            throw new BusinessException("菜品与套餐必须二选一");
        }
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(BaseContext.require().getId());
        // 未用到的一侧写 0、口味空串：配合 uk_user_goods，也是原子加购的前提
        cart.setDishId(dto.getDishId() == null ? 0L : dto.getDishId());
        cart.setSetmealId(dto.getSetmealId() == null ? 0L : dto.getSetmealId());
        cart.setDishFlavor(dto.getDishFlavor() == null ? "" : dto.getDishFlavor());
        if (dto.getDishId() != null) {
            Dish dish = dishService.getById(dto.getDishId());
            if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) {
                throw new BusinessException("菜品不存在或已停售");
            }
            if (dish.getStock() <= 0) {
                throw new BusinessException("菜品已售罄：" + dish.getName());
            }
            cart.setName(dish.getName());
            cart.setImage(dish.getImage());
            cart.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = setmealService.getById(dto.getSetmealId());
            if (setmeal == null || !Integer.valueOf(1).equals(setmeal.getStatus())) {
                throw new BusinessException("套餐不存在或已停售");
            }
            if (setmeal.getStock() <= 0) {
                throw new BusinessException("套餐已售罄：" + setmeal.getName());
            }
            cart.setName(setmeal.getName());
            cart.setImage(setmeal.getImage());
            cart.setAmount(setmeal.getPrice());
        }
        baseMapper.insertOrIncrement(cart);
    }

    @Override
    public List<ShoppingCart> listMine() {
        Long userId = BaseContext.require().getId();
        return lambdaQuery().eq(ShoppingCart::getUserId, userId)
                .orderByAsc(ShoppingCart::getCreateTime)
                .list();
    }

    @Override
    public void sub(ShoppingCartDTO dto) {
        Long userId = BaseContext.require().getId();
        Long dishId = dto.getDishId() == null ? 0L : dto.getDishId();
        Long setmealId = dto.getSetmealId() == null ? 0L : dto.getSetmealId();
        String flavor = dto.getDishFlavor() == null ? "" : dto.getDishFlavor();
        // 先原子减；减不动（已是 1 份）说明该删行
        int rows = baseMapper.decrement(userId, dishId, setmealId, flavor);
        if (rows == 0) {
            remove(new LambdaQueryWrapper<ShoppingCart>()
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getDishId, dishId)
                    .eq(ShoppingCart::getSetmealId, setmealId)
                    .eq(ShoppingCart::getDishFlavor, flavor));
        }
    }

    @Override
    public void cleanMine() {
        Long userId = BaseContext.require().getId();
        remove(new LambdaQueryWrapper<ShoppingCart>().eq(ShoppingCart::getUserId, userId));
    }
}
