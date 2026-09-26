package com.smartlife.server.controller.admin;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.vo.OrderVO;
import com.smartlife.server.service.IOrderService;
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

/** 管理端订单巡检：全平台查询 + 客服兜底处置（旁路归属校验，均有审计） */
@RestController
@RequestMapping("/admin/order")
@Validated
@Tag(name = "管理端-订单巡检")
public class AdminOrderController {

    private final IOrderService orderService;

    public AdminOrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "全平台订单分页，可按状态与店铺过滤")
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageAll(status, shopId, current, size));
    }

    @Operation(summary = "客服代接单（待接单 → 已接单）")
    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable @Positive Long id) {
        orderService.adminAccept(id);
        return Result.ok();
    }

    @Operation(summary = "客服代完成（派送中 → 已完成）")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable @Positive Long id) {
        orderService.adminComplete(id);
        return Result.ok();
    }

    @Operation(summary = "客服代取消（待接单/已接单/派送中 → 已取消，退款+回补库存+退券）")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable @Positive Long id,
                               @RequestParam(required = false) String reason) {
        orderService.adminCancel(id, reason);
        return Result.ok();
    }
}
