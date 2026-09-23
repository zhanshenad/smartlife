package com.smartlife.server.controller.user;

import com.smartlife.common.result.PageResult;
import com.smartlife.common.result.Result;
import com.smartlife.pojo.dto.OrdersSubmitDTO;
import com.smartlife.pojo.vo.OrderSubmitVO;
import com.smartlife.pojo.vo.OrderVO;
import com.smartlife.server.service.IOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端订单：下单 → 支付 → 查询/取消。
 */
@RestController
@RequestMapping("/order")
@Validated
public class OrderController {

    private final IOrderService orderService;

    public OrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody @Valid OrdersSubmitDTO dto) {
        return Result.ok(orderService.submit(dto));
    }

    /** 模拟支付：待支付 → 待接单 */
    @PostMapping("/{id}/payment")
    public Result<Void> payment(@PathVariable @Positive Long id) {
        orderService.pay(id);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageMine(status, current, size));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable @Positive Long id) {
        return Result.ok(orderService.detailMine(id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable @Positive Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    /** 催单：推送给商家 */
    @PostMapping("/{id}/reminder")
    public Result<Void> reminder(@PathVariable @Positive Long id) {
        orderService.reminder(id);
        return Result.ok();
    }
}
