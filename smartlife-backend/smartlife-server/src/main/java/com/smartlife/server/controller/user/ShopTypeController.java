package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.ShopType;
import com.smartlife.server.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    private final IShopTypeService shopTypeService;

    public ShopTypeController(IShopTypeService shopTypeService) {
        this.shopTypeService = shopTypeService;
    }

    /** 分类列表，互斥锁缓存 */
    @GetMapping("/list")
    public Result<List<ShopType>> list() {
        return Result.ok(shopTypeService.listSorted());
    }
}
