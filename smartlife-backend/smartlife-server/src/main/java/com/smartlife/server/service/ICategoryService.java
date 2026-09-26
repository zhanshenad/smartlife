package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.entity.Category;

/** 菜品/套餐分类字典（平台级，管理端维护） */
public interface ICategoryService extends IService<Category> {

    /** 分页查询，可按类型过滤，type 升序 + sort 升序 */
    PageResult<Category> pageQuery(Integer type, long current, long size);

    /** 新增分类，默认启用 */
    void saveCategory(Category category);

    /** 修改分类。被商品引用期间仅允许改名/排序，不允许改 type */
    void updateCategory(Category category);

    /** 删除分类。分类下还有菜品或套餐则拒绝 */
    void deleteCategory(Long id);

    /** 启用/禁用 */
    void startStop(Long id, int status);
}
