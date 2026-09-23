package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.ShopDTO;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.vo.ShopVO;

import java.util.List;

public interface IShopService extends IService<Shop> {

    /** 店铺详情，逻辑过期缓存 */
    Shop queryById(Long id);

    /** 按类型分页，按销量倒序 */
    PageResult<Shop> queryOfType(Long typeId, long current, long size);

    /** 附近店铺，GEO 按距离升序分页 */
    List<ShopVO> queryNearby(Double x, Double y, Long typeId, long current, long size);

    /** 商家端更新资料：归属校验 + 改库 + 删缓存 */
    void updateShop(ShopDTO dto);

    /** 商家定位自己的店：菜品/套餐/订单等商家端操作共用 */
    Long requireMyShopId();
}
