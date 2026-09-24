package com.smartlife.server.controller.user;

import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.VoucherOrderVO;
import com.smartlife.server.service.IVoucherOrderService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端领券：普通券同步领取、秒杀券异步抢购、我的券包。
 */
@RestController
@RequestMapping("/voucher")
@Validated
public class UserVoucherOrderController {

    private final IVoucherOrderService voucherOrderService;

    public UserVoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    /** 普通券领取（同步，返回即已入包） */
    @PostMapping("/{id}/grab")
    public Result<Void> grab(@PathVariable @Positive Long id) {
        voucherOrderService.grabVoucher(id);
        return Result.ok();
    }

    /** 秒杀抢购（异步，返回 orderId，此时订单尚未落库） */
    @PostMapping("/{id}/seckill")
    public Result<Long> seckill(@PathVariable @Positive Long id) {
        return Result.ok(voucherOrderService.seckillVoucher(id));
    }

    @GetMapping("/order/list")
    public Result<List<VoucherOrderVO>> listMy() {
        return Result.ok(voucherOrderService.listMy());
    }
}
