package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.server.service.IVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

/**
 * 商家端券管理。/merchant/** 由 AuthInterceptor 限定 role=2/3。
 */
@RestController
@RequestMapping("/merchant/voucher")
@Validated
public class MerchantVoucherController {

    private final IVoucherService voucherService;

    public MerchantVoucherController(IVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Operation(summary = "发券")
    @PostMapping
    public Result<Void> save(@RequestBody @Valid VoucherDTO dto) {
        voucherService.addVoucher(dto);
        return Result.ok();
    }

    @Operation(summary = "本店全部券")
    @GetMapping("/list")
    public Result<List<Voucher>> list() {
        return Result.ok(voucherService.listMyShop());
    }

    @Operation(summary = "改券（秒杀券只允许改基础字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive Long id,
                               @RequestBody @Valid VoucherDTO dto) {
        voucherService.updateVoucher(id, dto);
        return Result.ok();
    }

    @Operation(summary = "上下架（已领的券仍可核销）")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> startStop(@PathVariable @Positive Long id,
                                  @PathVariable Integer status) {
        voucherService.startStop(id, status);
        return Result.ok();
    }

    @Operation(summary = "删券（有领取记录拒绝，只能下架）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable @Positive Long id) {
        voucherService.removeVoucher(id);
        return Result.ok();
    }
}
