package com.smartlife.server.controller.admin;

import com.smartlife.common.result.Result;
import com.smartlife.server.service.IAdminShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端店铺治理：停业整顿 / 恢复 / 删除（均有审计） */
@RestController
@RequestMapping("/admin/shop")
@Validated
@Tag(name = "管理端-店铺治理")
public class AdminShopController {

    private final IAdminShopService adminShopService;

    public AdminShopController(IAdminShopService adminShopService) {
        this.adminShopService = adminShopService;
    }

    @Operation(summary = "停业整顿（0）/ 恢复（1）")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable @Positive Long id,
                                     @PathVariable Integer status) {
        adminShopService.changeStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除店铺（有未完结订单则拒绝）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable @Positive Long id) {
        adminShopService.remove(id);
        return Result.ok();
    }
}
