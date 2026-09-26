package com.smartlife.server.controller.admin;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.entity.AuditLog;
import com.smartlife.server.service.IAdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端操作审计查询：谁在什么时候批了谁、封了谁 */
@RestController
@RequestMapping("/admin/audit")
@Tag(name = "管理端-操作审计")
public class AdminAuditController {

    private final IAdminAuditService adminAuditService;

    public AdminAuditController(IAdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @Operation(summary = "审计分页，可按动作/目标类型/目标 id 过滤")
    @GetMapping("/page")
    public Result<PageResult<AuditLog>> page(@RequestParam(required = false) String action,
                                             @RequestParam(required = false) String targetType,
                                             @RequestParam(required = false) Long targetId,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminAuditService.page(action, targetType, targetId, current, size));
    }
}
