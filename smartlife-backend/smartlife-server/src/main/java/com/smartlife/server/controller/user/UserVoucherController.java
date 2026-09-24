package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.VoucherVO;
import com.smartlife.server.service.IVoucherService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端券浏览：某店上架中的券（含秒杀库存与时间窗）。
 * 领取/抢购在 UserVoucherOrderController，需登录。
 */
@RestController
@RequestMapping("/voucher")
@Validated
public class UserVoucherController {

    private final IVoucherService voucherService;

    public UserVoucherController(IVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping("/list")
    public Result<List<VoucherVO>> list(@RequestParam @Positive Long shopId) {
        return Result.ok(voucherService.listByShop(shopId));
    }
}
