package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.SetmealDTO;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.vo.SetmealVO;

import java.util.List;

public interface ISetmealService extends IService<Setmeal> {

    /** 新增套餐 + 关联菜品（两表一事务），快照由服务端回查回填 */
    void saveWithDish(SetmealDTO dto);

    /** 商家端分页查自己店的套餐 */
    PageResult<SetmealVO> pageQuery(Long categoryId, Integer status, String name, long current, long size);

    /** 详情（含关联菜品），归属校验 */
    SetmealVO getByIdWithDish(Long id);

    /** 修改套餐 + 关联删旧插新 */
    void updateWithDish(SetmealDTO dto);

    /** 批量删除：起售中的拒绝 */
    void deleteByIds(List<Long> ids);

    /** 起售/停售；起售时校验关联菜品全部在售 */
    void startStop(Long id, Integer status);

    /** 用户端：按店+分类查起售套餐，走随机 TTL 缓存 */
    List<SetmealVO> listOnSale(Long shopId, Long categoryId);
}
