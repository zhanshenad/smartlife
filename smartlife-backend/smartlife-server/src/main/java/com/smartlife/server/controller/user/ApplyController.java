package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.MerchantApplyDTO;
import com.smartlife.pojo.entity.MerchantApply;
import com.smartlife.server.service.IMerchantApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户端入驻申请：提交与进度查询 */
@RestController
@RequestMapping("/apply")
@Tag(name = "用户端-入驻申请")
public class ApplyController {

    private final IMerchantApplyService applyService;

    public ApplyController(IMerchantApplyService applyService) {
        this.applyService = applyService;
    }

    @Operation(summary = "提交入驻申请")
    @PostMapping("/submit")
    public Result<Long> submit(@RequestBody @Valid MerchantApplyDTO dto) {
        return Result.ok(applyService.submit(dto));
    }

    @Operation(summary = "我最近的申请（查进度）")
    @GetMapping("/my")
    public Result<MerchantApply> my() {
        return Result.ok(applyService.myLatest());
    }
}
