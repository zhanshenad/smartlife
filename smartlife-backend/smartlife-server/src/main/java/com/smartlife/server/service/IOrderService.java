package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.OrdersSubmitDTO;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.vo.OrderSubmitVO;
import com.smartlife.pojo.vo.OrderVO;

public interface IOrderService extends IService<Orders> {

    /** 从购物车下单：跨店校验 + 排序扣库存 + 建订单/明细 + 清购物车，一个事务 */
    OrderSubmitVO submit(OrdersSubmitDTO dto);

    /** 模拟支付：待支付 → 待接单，并触发商家来单提醒（P3-8） */
    void pay(Long orderId);

    /** 用户催单：推送给订单所属商家 */
    void reminder(Long orderId);

    /** 用户取消：仅待支付可取消，回补库存 + 退券 */
    void cancel(Long orderId);

    /** 超时取消：MQ 超时队列与兜底任务共用；非待支付状态静默跳过 */
    void timeoutCancel(Long orderId);

    /** 用户订单分页（自己的），带店铺名 */
    PageResult<OrderVO> pageMine(Integer status, long current, long size);

    /** 用户订单详情（自己的），带明细 */
    OrderVO detailMine(Long orderId);

    /** 商家订单分页（自己店的） */
    PageResult<OrderVO> pageShop(Integer status, long current, long size);

    /** 商家接单：待接单 → 已接单 */
    void accept(Long orderId);

    /** 商家拒单：待接单 → 已取消，回补库存，模拟退款 */
    void reject(Long orderId, String reason);

    /** 商家派送：已接单 → 派送中 */
    void delivery(Long orderId);

    /** 商家完成：派送中 → 已完成 */
    void complete(Long orderId);
}
