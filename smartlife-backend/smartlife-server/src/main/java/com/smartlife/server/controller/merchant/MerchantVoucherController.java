package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.server.service.IVoucherService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping
    public Result<Void> save(@RequestBody @Valid VoucherDTO dto) {
        voucherService.addVoucher(dto);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<Voucher>> list() {
        return Result.ok(voucherService.listMyShop());
    }
}
