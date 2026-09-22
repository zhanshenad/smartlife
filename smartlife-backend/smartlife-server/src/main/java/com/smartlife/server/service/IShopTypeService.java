package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.entity.ShopType;

import java.util.List;

public interface IShopTypeService extends IService<ShopType> {

    /** 全部分类，按 sort 升序 */
    List<ShopType> listSorted();
}
