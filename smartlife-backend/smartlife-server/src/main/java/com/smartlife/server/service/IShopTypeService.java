package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.entity.ShopType;

import java.util.List;

public interface IShopTypeService extends IService<ShopType> {

    /** 全部分类，按 sort 升序 */
    List<ShopType> listSorted();

    /** 新增类型 */
    void saveType(ShopType shopType);

    /** 修改类型 */
    void updateType(ShopType shopType);

    /** 删除类型。仍有店铺挂在该类型下则拒绝 */
    void deleteType(Long id);
}
