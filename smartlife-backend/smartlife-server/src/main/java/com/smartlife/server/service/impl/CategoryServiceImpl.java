package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.Category;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.server.mapper.CategoryMapper;
import com.smartlife.server.service.ICategoryService;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.ISetmealService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 分类字典。删除/禁用前校验商品引用，防止把在售商品挂到死分类上 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    private final IDishService dishService;
    private final ISetmealService setmealService;

    public CategoryServiceImpl(IDishService dishService, ISetmealService setmealService) {
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    @Override
    public PageResult<Category> pageQuery(Integer type, long current, long size) {
        Page<Category> page = lambdaQuery()
                .eq(type != null, Category::getType, type)
                .orderByAsc(Category::getType)
                .orderByAsc(Category::getSort)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public void saveCategory(Category category) {
        if (lambdaQuery().eq(Category::getName, category.getName())
                .eq(category.getType() != null, Category::getType, category.getType())
                .exists()) {
            throw new BusinessException("同名分类已存在");
        }
        category.setId(null);
        if (category.getStatus() == null) {
            category.setStatus(StatusConstants.Common.ENABLED);
        }
        save(category);
    }

    @Override
    public void updateCategory(Category category) {
        Category existed = getById(category.getId());
        if (existed == null) {
            throw new BusinessException("分类不存在");
        }
        // type 决定商品挂载关系，中途改型会造成引用错乱
        if (category.getType() != null && !category.getType().equals(existed.getType())) {
            throw new BusinessException("分类类型不允许修改");
        }
        updateById(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Long dishes = dishService.lambdaQuery().eq(Dish::getCategoryId, id).count();
        Long setmeals = setmealService.lambdaQuery().eq(Setmeal::getCategoryId, id).count();
        if ((dishes != null && dishes > 0) || (setmeals != null && setmeals > 0)) {
            throw new BusinessException("分类下还有菜品或套餐，不能删除");
        }
        removeById(id);
    }

    @Override
    public void startStop(Long id, int status) {
        if (status != StatusConstants.Common.DISABLED && status != StatusConstants.Common.ENABLED) {
            throw new BusinessException("非法的状态值");
        }
        if (!lambdaUpdate().eq(Category::getId, id).set(Category::getStatus, status).update()) {
            throw new BusinessException("分类不存在");
        }
    }
}
