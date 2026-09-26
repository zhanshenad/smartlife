package com.smartlife.server.controller.admin;

import com.smartlife.common.result.Result;
import com.smartlife.server.service.IAdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端商品治理：违规下架/恢复（旁路商家归属，均有审计） */
@RestController
@RequestMapping("/admin/product")
@Validated
@Tag(name = "管理端-商品治理")
public class AdminProductController {

    private final IAdminProductService adminProductService;

    public AdminProductController(IAdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @Operation(summary = "菜品下架（0）/ 恢复（1）")
    @PutMapping("/dish/{id}/status/{status}")
    public Result<Void> dish(@PathVariable @Positive Long id, @PathVariable Integer status) {
        adminProductService.dishStartStop(id, status);
        return Result.ok();
    }

    @Operation(summary = "套餐下架（0）/ 恢复（1）")
    @PutMapping("/setmeal/{id}/status/{status}")
    public Result<Void> setmeal(@PathVariable @Positive Long id, @PathVariable Integer status) {
        adminProductService.setmealStartStop(id, status);
        return Result.ok();
    }

    @Operation(summary = "违规券强制下架（2）/ 恢复上架（1）")
    @PutMapping("/voucher/{id}/status/{status}")
    public Result<Void> voucher(@PathVariable @Positive Long id, @PathVariable Integer status) {
        adminProductService.voucherStartStop(id, status);
        return Result.ok();
    }
}
