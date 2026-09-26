package com.smartlife.server.controller.admin;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.MerchantApply;
import com.smartlife.server.service.IMerchantApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端入驻审核：分页、通过（自动建店）、驳回 */
@RestController
@RequestMapping("/admin/apply")
@Validated
@Tag(name = "管理端-入驻审核")
public class AdminApplyController {

    private final IMerchantApplyService applyService;

    public AdminApplyController(IMerchantApplyService applyService) {
        this.applyService = applyService;
    }

    @Operation(summary = "申请单分页（status：0 待审 1 通过 2 驳回）")
    @GetMapping("/page")
    public Result<PageResult<MerchantApply>> page(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.ok(applyService.page(status, current, size));
    }

    @Operation(summary = "审核通过：同事务建店、回填 shopId、升商家角色")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable @Positive Long id) {
        applyService.approve(id);
        return Result.ok();
    }

    @Operation(summary = "驳回申请")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable @Positive Long id,
                               @RequestParam @NotBlank String remark) {
        applyService.reject(id, remark);
        return Result.ok();
    }
}
