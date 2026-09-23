package com.smartlife.server.controller.merchant;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.OrderVO;
import com.smartlife.server.service.IOrderService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端订单履约：接单 → 派送 → 完成，另有拒单。每步都校验归属与状态。
 */
@RestController
@RequestMapping("/merchant/order")
@Validated
public class MerchantOrderController {

    private final IOrderService orderService;

    public MerchantOrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageShop(status, current, size));
    }

    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable @Positive Long id) {
        orderService.accept(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable @Positive Long id,
                               @RequestParam(required = false) String reason) {
        orderService.reject(id, reason);
        return Result.ok();
    }

    @PutMapping("/{id}/delivery")
    public Result<Void> delivery(@PathVariable @Positive Long id) {
        orderService.delivery(id);
        return Result.ok();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable @Positive Long id) {
        orderService.complete(id);
        return Result.ok();
    }
}
