package com.smartlife.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.common.constant.AuditConstants;
import com.smartlife.common.constant.MQConstants;
import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.result.PageResult;
import com.smartlife.common.util.RedisIdWorker;
import com.smartlife.pojo.dto.OrderTimeoutMessage;
import com.smartlife.pojo.dto.OrdersSubmitDTO;
import com.smartlife.pojo.entity.Dish;
import com.smartlife.pojo.entity.OrderDetail;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.entity.Setmeal;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.entity.ShoppingCart;
import com.smartlife.pojo.vo.OrderSubmitVO;
import com.smartlife.pojo.vo.OrderVO;
import com.smartlife.server.mapper.DishMapper;
import com.smartlife.server.mapper.OrderDetailMapper;
import com.smartlife.server.mapper.OrderMapper;
import com.smartlife.server.mapper.SetmealMapper;
import com.smartlife.server.service.IOrderService;
import com.smartlife.server.service.ISetmealService;
import com.smartlife.server.service.IShopService;
import com.smartlife.server.service.IShoppingCartService;
import com.smartlife.server.service.IDishService;
import com.smartlife.server.service.IVoucherOrderService;
import com.smartlife.server.service.AuditRecorder;
import com.smartlife.server.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单：用户端下单/支付/取消 + 商家端状态机。
 * 扣库存顺序全局统一"先套餐（id 升序）后菜品（id 升序）"，
 * 两个订单交叉持锁也不可能互等，死锁在结构上被排除（§5.2.7 ③）。
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements IOrderService {

    /** 模拟支付方式固定记微信（tb_orders.pay_method=1） */
    private static final int PAY_METHOD_MOCK_WECHAT = 1;

    private final IDishService dishService;
    private final ISetmealService setmealService;
    private final IShoppingCartService shoppingCartService;
    private final IShopService shopService;
    private final IVoucherOrderService voucherOrderService;
    private final OrderDetailMapper orderDetailMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    private final RedisIdWorker idWorker;
    private final RabbitTemplate rabbitTemplate;
    private final AuditRecorder auditRecorder;
    private final StringRedisTemplate redis;

    public OrderServiceImpl(IDishService dishService, ISetmealService setmealService,
                            IShoppingCartService shoppingCartService, IShopService shopService,
                            IVoucherOrderService voucherOrderService, OrderDetailMapper orderDetailMapper,
                            DishMapper dishMapper, SetmealMapper setmealMapper,
                            RedisIdWorker idWorker, RabbitTemplate rabbitTemplate,
                            AuditRecorder auditRecorder, StringRedisTemplate redis) {
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.shoppingCartService = shoppingCartService;
        this.shopService = shopService;
        this.voucherOrderService = voucherOrderService;
        this.orderDetailMapper = orderDetailMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
        this.idWorker = idWorker;
        this.rabbitTemplate = rabbitTemplate;
        this.auditRecorder = auditRecorder;
        this.redis = redis;
    }

    // ==================== 用户端 ====================

    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO dto) {
        Long userId = BaseContext.require().getId();

        // 防重复提交：库存乐观锁只保证不超卖，不保证"一个用户只下这一单"——
        // 双击的两个请求各生成一个订单号，在数据层面都是合法订单，只能在这里拦（§5.2.7 ⑤）。
        String submitTokenKey = RedisConstants.ORDER_SUBMIT_TOKEN_KEY + userId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(submitTokenKey, "1",
                RedisConstants.ORDER_SUBMIT_TOKEN_TTL_SECONDS, TimeUnit.SECONDS))) {
            throw new BusinessException("请勿重复提交");
        }
        // token 是 Redis 写，不参与数据库事务，回滚不会撤销它；业务失败时手动释放，
        // 让用户改完购物车能立刻重试，不用白等 5 秒
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    redis.delete(submitTokenKey);
                }
            }
        });

        List<ShoppingCart> carts = shoppingCartService.listMine();
        if (carts.isEmpty()) {
            throw new BusinessException("购物车为空，无法下单");
        }
        // 跨店校验 + 在售校验，快照金额以商品表为准
        List<ShoppingCart> setmealItems = carts.stream()
                .filter(c -> c.getSetmealId() != 0).sorted(Comparator.comparing(ShoppingCart::getSetmealId)).toList();
        List<ShoppingCart> dishItems = carts.stream()
                .filter(c -> c.getDishId() != 0).sorted(Comparator.comparing(ShoppingCart::getDishId)).toList();

        Map<Long, Setmeal> setmealMap = loadSetmeals(setmealItems);
        Map<Long, Dish> dishMap = loadDishes(dishItems);
        for (ShoppingCart c : setmealItems) {
            Setmeal s = setmealMap.get(c.getSetmealId());
            checkItem(s == null ? null : s.getShopId(), s == null ? null : s.getStatus(),
                    dto.getShopId(), "套餐");
        }
        for (ShoppingCart c : dishItems) {
            Dish d = dishMap.get(c.getDishId());
            checkItem(d == null ? null : d.getShopId(), d == null ? null : d.getStatus(),
                    dto.getShopId(), "菜品");
        }

        // 扣库存：顺序固定（上面已排好），乐观锁，0 行即库存不足回滚全部
        for (ShoppingCart c : setmealItems) {
            if (setmealMapper.deductStock(c.getSetmealId(), c.getNumber()) == 0) {
                throw new BusinessException("库存不足：" + c.getName());
            }
        }
        for (ShoppingCart c : dishItems) {
            if (dishMapper.deductStock(c.getDishId(), c.getNumber()) == 0) {
                throw new BusinessException("库存不足：" + c.getName());
            }
        }

        // 订单主表：金额服务端按商品表现价计算
        int amount = 0;
        for (ShoppingCart c : carts) {
            amount += unitPrice(c, setmealMap, dishMap) * c.getNumber();
        }
        // 券核销（同事务）：归属/门槛校验 + CAS 改状态，任一步失败全单回滚
        int discount = dto.getVoucherOrderId() == null ? 0
                : voucherOrderService.redeem(dto.getVoucherOrderId(), userId, dto.getShopId(), amount);
        Orders order = new Orders();
        order.setNumber(String.valueOf(idWorker.nextId(RedisConstants.ORDER_ID_KEY)));
        order.setStatus(StatusConstants.Order.PENDING_PAYMENT);
        order.setUserId(userId);
        order.setShopId(dto.getShopId());
        order.setOrderTime(LocalDateTime.now());
        order.setPayMethod(PAY_METHOD_MOCK_WECHAT);
        order.setPayStatus(StatusConstants.Pay.UN_PAID);
        order.setAmount(amount);
        order.setDiscountAmount(discount);
        order.setPayAmount(amount - discount);
        order.setVoucherOrderId(dto.getVoucherOrderId());
        order.setRemark(dto.getRemark());
        order.setPhone(dto.getPhone());
        order.setAddress(dto.getAddress());
        order.setUserName(BaseContext.require().getNickname());
        order.setConsignee(dto.getConsignee());
        save(order);

        // 明细快照
        for (ShoppingCart c : carts) {
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(order.getId());
            detail.setName(c.getName());
            detail.setImage(c.getImage());
            detail.setDishId(c.getDishId() == 0 ? null : c.getDishId());
            detail.setSetmealId(c.getSetmealId() == 0 ? null : c.getSetmealId());
            detail.setDishFlavor(c.getDishFlavor() == null || c.getDishFlavor().isEmpty()
                    ? null : c.getDishFlavor());
            detail.setNumber(c.getNumber());
            detail.setAmount(unitPrice(c, setmealMap, dishMap) * c.getNumber());
            orderDetailMapper.insert(detail);
        }

        shoppingCartService.cleanMine();

        // 事务提交后才发延迟消息：回滚了就不发，避免"订单没落库、超时消息先到期"的幻影取消。
        // 这里不加同步确认——afterCommit 里没有请求线程可等，丢了有每分钟兜底扫表自愈
        Long orderId = order.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(MQConstants.ORDER_EXCHANGE, MQConstants.ORDER_DELAY_ROUTING_KEY,
                        new OrderTimeoutMessage(orderId));
            }
        });

        OrderSubmitVO vo = new OrderSubmitVO();
        vo.setId(order.getId());
        vo.setNumber(order.getNumber());
        vo.setAmount(amount);
        vo.setPayAmount(amount - discount);
        return vo;
    }

    @Override
    public void pay(Long orderId) {
        Orders order = requireMyOrder(orderId);
        requireStatus(order, StatusConstants.Order.PENDING_PAYMENT, "仅待支付订单可支付");
        casTransition(orderId, StatusConstants.Order.PENDING_PAYMENT, upd -> upd
                .set(Orders::getStatus, StatusConstants.Order.TO_BE_CONFIRMED)
                .set(Orders::getPayStatus, StatusConstants.Pay.PAID)
                .set(Orders::getCheckoutTime, LocalDateTime.now()));
        notifyMerchant(order, "来单提醒：订单 " + order.getNumber() + " 已支付，请及时处理");
    }

    @Override
    public void reminder(Long orderId) {
        Orders order = requireMyOrder(orderId);
        if (order.getStatus() == null || order.getStatus() < StatusConstants.Order.TO_BE_CONFIRMED
                || order.getStatus() > StatusConstants.Order.CONFIRMED) {
            throw new BusinessException("当前状态无需催单");
        }
        notifyMerchant(order, "催单提醒：订单 " + order.getNumber() + " 的用户正在催单");
    }

    @Override
    @Transactional
    public void cancel(Long orderId) {
        Orders order = requireMyOrder(orderId);
        requireStatus(order, StatusConstants.Order.PENDING_PAYMENT, "仅待支付订单可取消");
        cancelInternal(order, "用户取消");
    }

    @Override
    @Transactional
    public void timeoutCancel(Long orderId) {
        Orders order = getById(orderId);
        // 已支付/已取消/不存在都静默跳过：延迟消息到期时订单状态大概率已流转
        if (order == null || order.getStatus() == null
                || order.getStatus() != StatusConstants.Order.PENDING_PAYMENT) {
            return;
        }
        try {
            cancelInternal(order, "超时未支付，系统自动取消");
        } catch (BusinessException e) {
            // 检查通过后恰被并发支付/取消，CAS 让位：正常竞态，不该按消费失败处理
            log.info("超时取消让位（订单状态已被并发流转）：orderId={}, {}", orderId, e.getMessage());
        }
    }

    @Override
    public PageResult<OrderVO> pageMine(Integer status, long current, long size) {
        Long userId = BaseContext.require().getId();
        Page<Orders> page = lambdaQuery()
                .eq(Orders::getUserId, userId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getOrderTime)
                .page(new Page<>(current, size));
        List<OrderVO> vos = attachShopName(page.getRecords());
        return PageResult.of(page.getTotal(), vos);
    }

    @Override
    public OrderVO detailMine(Long orderId) {
        Orders order = requireMyOrder(orderId);
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        Shop shop = shopService.getById(order.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getName());
        }
        vo.setDetailList(orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orderId)));
        return vo;
    }

    // ==================== 商家端 ====================

    @Override
    public PageResult<OrderVO> pageShop(Integer status, long current, long size) {
        Long shopId = shopService.requireMyShopId();
        Page<Orders> page = lambdaQuery()
                .eq(Orders::getShopId, shopId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getOrderTime)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), attachShopName(page.getRecords()));
    }

    @Override
    public void accept(Long orderId) {
        Orders order = requireShopOrder(orderId);
        requireStatus(order, StatusConstants.Order.TO_BE_CONFIRMED, "仅待接单订单可接单");
        casTransition(orderId, StatusConstants.Order.TO_BE_CONFIRMED,
                upd -> upd.set(Orders::getStatus, StatusConstants.Order.CONFIRMED));
    }

    @Override
    @Transactional
    public void reject(Long orderId, String reason) {
        Orders order = requireShopOrder(orderId);
        requireStatus(order, StatusConstants.Order.TO_BE_CONFIRMED, "仅待接单订单可拒单");
        restoreStock(orderId);
        voucherOrderService.restore(order.getVoucherOrderId());
        casTransition(orderId, StatusConstants.Order.TO_BE_CONFIRMED, upd -> upd
                .set(Orders::getStatus, StatusConstants.Order.CANCELLED)
                .set(Orders::getPayStatus, StatusConstants.Pay.REFUND)
                .set(Orders::getRejectionReason, reason == null || reason.isBlank() ? "商家拒单" : reason)
                .set(Orders::getCancelTime, LocalDateTime.now()));
    }

    @Override
    public void delivery(Long orderId) {
        Orders order = requireShopOrder(orderId);
        requireStatus(order, StatusConstants.Order.CONFIRMED, "仅已接单订单可派送");
        casTransition(orderId, StatusConstants.Order.CONFIRMED,
                upd -> upd.set(Orders::getStatus, StatusConstants.Order.DELIVERY_IN_PROGRESS));
    }

    @Override
    public void complete(Long orderId) {
        Orders order = requireShopOrder(orderId);
        requireStatus(order, StatusConstants.Order.DELIVERY_IN_PROGRESS, "仅派送中订单可完成");
        casTransition(orderId, StatusConstants.Order.DELIVERY_IN_PROGRESS, upd -> upd
                .set(Orders::getStatus, StatusConstants.Order.COMPLETED)
                .set(Orders::getDeliveryTime, LocalDateTime.now()));
    }

    // ==================== 管理端：订单巡检 ====================

    @Override
    public PageResult<OrderVO> pageAll(Integer status, Long shopId, long current, long size) {
        Page<Orders> page = lambdaQuery()
                .eq(status != null, Orders::getStatus, status)
                .eq(shopId != null, Orders::getShopId, shopId)
                .orderByDesc(Orders::getOrderTime)
                .page(new Page<>(current, size));
        return PageResult.of(page.getTotal(), attachShopName(page.getRecords()));
    }

    @Override
    @Transactional
    public void adminAccept(Long orderId) {
        Orders order = requireOrder(orderId);
        requireStatus(order, StatusConstants.Order.TO_BE_CONFIRMED, "仅待接单订单可代接单");
        casTransition(orderId, StatusConstants.Order.TO_BE_CONFIRMED,
                upd -> upd.set(Orders::getStatus, StatusConstants.Order.CONFIRMED));
        auditRecorder.record(AuditConstants.ACTION_ORDER_ADMIN_ACCEPT,
                AuditConstants.TARGET_ORDER, orderId);
    }

    @Override
    @Transactional
    public void adminComplete(Long orderId) {
        Orders order = requireOrder(orderId);
        requireStatus(order, StatusConstants.Order.DELIVERY_IN_PROGRESS, "仅派送中订单可代完成");
        casTransition(orderId, StatusConstants.Order.DELIVERY_IN_PROGRESS, upd -> upd
                .set(Orders::getStatus, StatusConstants.Order.COMPLETED)
                .set(Orders::getDeliveryTime, LocalDateTime.now()));
        auditRecorder.record(AuditConstants.ACTION_ORDER_ADMIN_COMPLETE,
                AuditConstants.TARGET_ORDER, orderId);
    }

    @Override
    @Transactional
    public void adminCancel(Long orderId, String reason) {
        Orders order = requireOrder(orderId);
        if (order.getStatus() == null || order.getStatus() < StatusConstants.Order.TO_BE_CONFIRMED
                || order.getStatus() > StatusConstants.Order.DELIVERY_IN_PROGRESS) {
            throw new BusinessException("仅待接单/已接单/派送中的订单可代取消");
        }
        restoreStock(orderId);
        voucherOrderService.restore(order.getVoucherOrderId());
        // 前驱是一组状态：CAS 用 IN 而非单值，并发流转时输掉的一方收到状态已变化
        boolean ok = lambdaUpdate()
                .eq(Orders::getId, orderId)
                .in(Orders::getStatus,
                        StatusConstants.Order.TO_BE_CONFIRMED,
                        StatusConstants.Order.CONFIRMED,
                        StatusConstants.Order.DELIVERY_IN_PROGRESS)
                .set(Orders::getStatus, StatusConstants.Order.CANCELLED)
                .set(Orders::getPayStatus, StatusConstants.Pay.REFUND)
                .set(Orders::getCancelTime, LocalDateTime.now())
                .set(Orders::getCancelReason,
                        "管理员代取消：" + (reason == null || reason.isBlank() ? "客服处置" : reason))
                .update();
        if (!ok) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        auditRecorder.record(AuditConstants.ACTION_ORDER_ADMIN_CANCEL,
                AuditConstants.TARGET_ORDER, orderId,
                Map.of("from", order.getStatus()));
    }

    // ==================== 私有辅助 ====================

    /** 取消的公共内核：回补库存 + 退券 + CAS 置取消态。用户取消 / 超时取消共用 */
    private void cancelInternal(Orders order, String reason) {
        restoreStock(order.getId());
        voucherOrderService.restore(order.getVoucherOrderId());
        casTransition(order.getId(), StatusConstants.Order.PENDING_PAYMENT, upd -> upd
                .set(Orders::getStatus, StatusConstants.Order.CANCELLED)
                .set(Orders::getPayStatus, StatusConstants.Pay.REFUND)
                .set(Orders::getCancelTime, LocalDateTime.now())
                .set(Orders::getCancelReason, reason));
    }

    /**
     * 状态流转走乐观锁：WHERE 带 status=前驱状态，并发双请求只有一个改得动，
     * 输的那个收到"状态已变化"。前置 requireStatus 只为给出友好提示。
     */
    private void casTransition(Long orderId, int expected,
                               Consumer<LambdaUpdateChainWrapper<Orders>> setter) {
        LambdaUpdateChainWrapper<Orders> upd = lambdaUpdate()
                .eq(Orders::getId, orderId)
                .eq(Orders::getStatus, expected);
        setter.accept(upd);
        if (!upd.update()) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
    }

    /** 推送给订单所属店铺的商家（sid = merchantId），不在线则跳过 */
    private void notifyMerchant(Orders order, String message) {
        Shop shop = shopService.getById(order.getShopId());
        if (shop != null) {
            WebSocketServer.sendTo(String.valueOf(shop.getMerchantId()), message);
        }
    }

    /** 购物车条目的权威单价：以商品表现价为准 */
    private int unitPrice(ShoppingCart c, Map<Long, Setmeal> setmealMap, Map<Long, Dish> dishMap) {
        return c.getSetmealId() != 0 ? setmealMap.get(c.getSetmealId()).getPrice()
                : dishMap.get(c.getDishId()).getPrice();
    }

    /** 商品归属与在售校验：不存在 / 不是这家店 / 已停售 都拒 */
    private void checkItem(Long itemShopId, Integer itemStatus, Long orderShopId, String kind) {
        if (itemShopId == null) {
            throw new BusinessException("购物车中的" + kind + "已不存在");
        }
        if (!itemShopId.equals(orderShopId)) {
            throw new BusinessException("购物车中存在其他店铺的商品，请先清空再下单");
        }
        if (itemStatus == null || itemStatus != StatusConstants.Common.ENABLED) {
            throw new BusinessException("购物车中存在已停售的商品，请移除后再下单");
        }
    }

    private Map<Long, Setmeal> loadSetmeals(List<ShoppingCart> items) {
        Set<Long> ids = items.stream().map(ShoppingCart::getSetmealId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : setmealService.listByIds(ids).stream()
                .collect(Collectors.toMap(Setmeal::getId, Function.identity()));
    }

    private Map<Long, Dish> loadDishes(List<ShoppingCart> items) {
        Set<Long> ids = items.stream().map(ShoppingCart::getDishId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : dishService.listByIds(ids).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    /** 按明细回补库存：取消 / 拒单时商品没出库，还回去。顺序与扣减一致防交叉持锁 */
    private void restoreStock(Long orderId) {
        List<OrderDetail> details = orderDetailMapper.selectList(
                        new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orderId))
                .stream()
                .sorted(Comparator.comparing((OrderDetail d) -> d.getSetmealId() == null ? 1 : 0)
                        .thenComparing(d -> d.getSetmealId() != null ? d.getSetmealId() : d.getDishId()))
                .toList();
        for (OrderDetail d : details) {
            if (d.getSetmealId() != null) {
                setmealMapper.restoreStock(d.getSetmealId(), d.getNumber());
            } else if (d.getDishId() != null) {
                dishMapper.restoreStock(d.getDishId(), d.getNumber());
            }
        }
    }

    private List<OrderVO> attachShopName(List<Orders> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> shopIds = orders.stream().map(Orders::getShopId).collect(Collectors.toSet());
        Map<Long, String> shopNames = shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getName));
        List<OrderVO> vos = new ArrayList<>(orders.size());
        for (Orders o : orders) {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(o, vo);
            vo.setShopName(shopNames.get(o.getShopId()));
            vos.add(vo);
        }
        return vos;
    }

    /** 管理端巡检用：只确认订单存在，不做归属校验（客服可旁路处置任意订单） */
    private Orders requireOrder(Long orderId) {
        Orders order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private Orders requireMyOrder(Long orderId) {
        Orders order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(BaseContext.require().getId())) {
            throw new BusinessException("只能操作自己的订单");
        }
        return order;
    }

    private Orders requireShopOrder(Long orderId) {
        Orders order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getShopId().equals(shopService.requireMyShopId())) {
            throw new BusinessException("只能操作自己店铺的订单");
        }
        return order;
    }

    private void requireStatus(Orders order, int expected, String message) {
        if (order.getStatus() == null || order.getStatus() != expected) {
            throw new BusinessException(message);
        }
    }
}
