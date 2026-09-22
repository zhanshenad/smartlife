package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.ShopDTO;
import com.smartlife.server.service.IShopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant/shop")
public class MerchantShopController {

    private final IShopService shopService;

    public MerchantShopController(IShopService shopService) {
        this.shopService = shopService;
    }

    /** 更新店铺资料：归属校验 + 改库 + 删缓存（Cache Aside） */
    @PutMapping
    public Result<Void> update(@RequestBody @Valid ShopDTO dto) {
        shopService.updateShop(dto);
        return Result.ok();
    }
}
