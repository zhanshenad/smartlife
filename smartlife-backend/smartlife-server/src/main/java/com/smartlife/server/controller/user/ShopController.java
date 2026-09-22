package com.smartlife.server.controller.user;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.vo.ShopVO;
import com.smartlife.server.service.IShopService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop")
@Validated
public class ShopController {

    private final IShopService shopService;

    public ShopController(IShopService shopService) {
        this.shopService = shopService;
    }

    /** 店铺详情，逻辑过期缓存。id 合法性校验是穿透防护的第一道（§5.3） */
    @GetMapping("/{id}")
    public Result<Shop> getById(@PathVariable @Positive Long id) {
        return Result.ok(shopService.queryById(id));
    }

    /** 压测对照组：直查 DB 不走缓存，仅供 JMeter 出"有无缓存 QPS"对比数字 */
    @GetMapping("/db/{id}")
    public Result<Shop> getByIdFromDb(@PathVariable Long id) {
        return Result.ok(shopService.getById(id));
    }

    /** 按类型分页 */
    @GetMapping("/of/type")
    public Result<PageResult<Shop>> ofType(@RequestParam Long typeId,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(shopService.queryOfType(typeId, current, size));
    }

    /** 附近店铺，GEO 按距离升序 */
    @GetMapping("/nearby")
    public Result<List<ShopVO>> nearby(@RequestParam Double x,
                                       @RequestParam Double y,
                                       @RequestParam Long typeId,
                                       @RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(shopService.queryNearby(x, y, typeId, current, size));
    }
}
