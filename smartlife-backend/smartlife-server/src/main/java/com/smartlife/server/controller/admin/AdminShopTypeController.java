package com.smartlife.server.controller.admin;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.ShopTypeDTO;
import com.smartlife.pojo.entity.ShopType;
import com.smartlife.server.service.IShopTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端店铺类型字典：增删改（改动后失效全量缓存） */
@RestController
@RequestMapping("/admin/shop-type")
@Validated
@Tag(name = "管理端-店铺类型")
public class AdminShopTypeController {

    private final IShopTypeService shopTypeService;

    public AdminShopTypeController(IShopTypeService shopTypeService) {
        this.shopTypeService = shopTypeService;
    }

    @Operation(summary = "全量列表")
    @GetMapping("/list")
    public Result<List<ShopType>> list() {
        return Result.ok(shopTypeService.listSorted());
    }

    @Operation(summary = "新增类型")
    @PostMapping
    public Result<Void> save(@RequestBody @Valid ShopTypeDTO dto) {
        ShopType type = new ShopType();
        BeanUtils.copyProperties(dto, type);
        shopTypeService.saveType(type);
        return Result.ok();
    }

    @Operation(summary = "修改类型")
    @PutMapping
    public Result<Void> update(@RequestBody @Valid ShopTypeDTO dto) {
        ShopType type = new ShopType();
        BeanUtils.copyProperties(dto, type);
        shopTypeService.updateType(type);
        return Result.ok();
    }

    @Operation(summary = "删除类型（仍有店铺使用则拒绝）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable @Positive Long id) {
        shopTypeService.deleteType(id);
        return Result.ok();
    }
}
