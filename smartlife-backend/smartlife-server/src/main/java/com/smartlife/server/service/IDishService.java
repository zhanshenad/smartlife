package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.DishDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.vo.DishVO;

import java.util.List;

public interface IDishService extends IService<Dish> {

    /** 新增菜品（连口味两表一事务），归属自动取登录商家的店 */
    void saveWithFlavor(DishDTO dto);

    /** 商家端分页查自己店的菜品，可按分类/状态/名称过滤 */
    PageResult<DishVO> pageQuery(Long categoryId, Integer status, String name, long current, long size);

    /** 详情（含口味），归属校验 */
    DishVO getByIdWithFlavor(Long id);

    /** 修改菜品 + 口味删旧插新 */
    void updateWithFlavor(DishDTO dto);

    /** 批量删除：起售中或被套餐关联的拒绝 */
    void deleteByIds(List<Long> ids);

    /** 起售/停售 */
    void startStop(Long id, Integer status);

    /** 用户端：按店+分类查起售菜品（含口味），走随机 TTL 缓存，空列表也缓存 */
    List<DishVO> listOnSale(Long shopId, Long categoryId);
}
