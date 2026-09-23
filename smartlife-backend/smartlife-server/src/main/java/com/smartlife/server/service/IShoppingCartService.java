package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.ShoppingCart;

import java.util.List;

public interface IShoppingCartService extends IService<ShoppingCart> {

    /** 加购一个，商品快照服务端回查；停售/售罄商品拒绝 */
    void add(ShoppingCartDTO dto);

    /** 当前用户购物车列表，按加购时间正序 */
    List<ShoppingCart> listMine();

    /** 减购一个，减到 0 删行 */
    void sub(ShoppingCartDTO dto);

    /** 清空当前用户购物车 */
    void cleanMine();
}
