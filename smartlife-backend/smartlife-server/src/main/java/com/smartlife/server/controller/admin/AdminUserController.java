package com.smartlife.server.controller.admin;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.UserVO;
import com.smartlife.server.service.IAdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端账号治理：全站账号分页、封禁/解封（封禁即时踢下线） */
@RestController
@RequestMapping("/admin/user")
@Validated
@Tag(name = "管理端-账号治理")
public class AdminUserController {

    private final IAdminUserService adminUserService;

    public AdminUserController(IAdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "全站账号分页")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@RequestParam(required = false) Integer role,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminUserService.page(role, status, current, size));
    }

    @Operation(summary = "封禁/解封账号（status：0 封禁 1 启用）")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable @Positive Long id,
                                     @PathVariable Integer status) {
        adminUserService.changeStatus(id, status);
        return Result.ok();
    }
}
