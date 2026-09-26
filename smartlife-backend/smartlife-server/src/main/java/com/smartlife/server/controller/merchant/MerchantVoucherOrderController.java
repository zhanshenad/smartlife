package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.Result;
import com.smartlife.server.service.IVoucherOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家端到店核销：扫用户券码核销，复用券域 redeem（归属/状态/门槛校验一致） */
@RestController
@RequestMapping("/merchant/voucher-order")
@Validated
@Tag(name = "商家端-到店核销")
public class MerchantVoucherOrderController {

    private final IVoucherOrderService voucherOrderService;

    public MerchantVoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @Operation(summary = "到店核销（返回抵扣额，单位分）")
    @PostMapping("/{id}/redeem")
    public Result<Integer> redeem(@PathVariable @Positive Long id) {
        return Result.ok(voucherOrderService.redeemOnSite(id));
    }
}
