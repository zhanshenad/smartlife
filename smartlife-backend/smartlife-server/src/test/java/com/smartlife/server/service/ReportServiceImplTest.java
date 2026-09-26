package com.smartlife.server.service;

import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.entity.OrderDetail;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.vo.ReportOverviewVO;
import com.smartlife.pojo.vo.ReportTopVO;
import com.smartlife.pojo.vo.ReportTrendVO;
import com.smartlife.server.mapper.OrderDetailMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 看板测试。造数口径：三笔订单（两笔已完成、一笔已取消），
 * 断言营业额只算已完成、完成率、日期轴补零、Top 按快照名聚合。
 */
@SpringBootTest
@Transactional
@DisplayName("看板：汇总/趋势/Top10")
class ReportServiceImplTest {

    private static final Long SHOP_ID = 1L;

    @Autowired
    private IReportService reportService;
    @Autowired
    private IOrderService orderService;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    private void newOrder(int status, int payAmount, String itemName, int itemNumber, int daysAgo) {
        Orders order = new Orders();
        order.setNumber("RPT-" + System.nanoTime());
        order.setStatus(status);
        order.setPayStatus(status == StatusConstants.Order.CANCELLED
                ? StatusConstants.Pay.REFUND : StatusConstants.Pay.PAID);
        order.setUserId(48L);
        order.setShopId(SHOP_ID);
        order.setOrderTime(LocalDateTime.now().minusDays(daysAgo).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        order.setPayMethod(1);
        order.setAmount(payAmount);
        order.setDiscountAmount(0);
        order.setPayAmount(payAmount);
        orderService.save(order);

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setName(itemName);
        detail.setNumber(itemNumber);
        detail.setAmount(payAmount);
        orderDetailMapper.insert(detail);
    }

    @Test
    @DisplayName("汇总：营业额只算已完成，完成率 = 有效/总数")
    void overviewCountsCompletedOnly() {
        LocalDate today = LocalDate.now();
        newOrder(StatusConstants.Order.COMPLETED, 1000, "热销A", 2, 0);
        newOrder(StatusConstants.Order.COMPLETED, 2000, "热销A", 1, 0);
        newOrder(StatusConstants.Order.CANCELLED, 9999, "被取消", 1, 0);

        ReportOverviewVO vo = reportService.overview(today, today, SHOP_ID);

        assertEquals(3000L, vo.getTurnover());
        assertEquals(3L, vo.getOrderCount());
        assertEquals(2L, vo.getValidOrderCount());
        assertEquals(2.0 / 3, vo.getOrderCompletionRate(), 0.001);
    }

    @Test
    @DisplayName("汇总：店铺过滤——别家店的订单不进来")
    void overviewFiltersShop() {
        LocalDate today = LocalDate.now();
        newOrder(StatusConstants.Order.COMPLETED, 5000, "本店单", 1, 0);

        ReportOverviewVO other = reportService.overview(today, today, 2L);
        assertEquals(0L, other.getTurnover(), "2 号店不应看到 1 号店订单");
    }

    @Test
    @DisplayName("趋势：日期轴补零，无数据的日子为 0 而不是缺行")
    void trendFillsZeroDays() {
        LocalDate today = LocalDate.now();
        newOrder(StatusConstants.Order.COMPLETED, 1500, "趋势单", 1, 0);

        ReportTrendVO vo = reportService.trend(today.minusDays(2), today, SHOP_ID);

        assertEquals(3, vo.getDateList().size());
        assertEquals(0L, vo.getTurnoverList().get(0), "两天前无订单应为 0");
        assertEquals(0L, vo.getTurnoverList().get(1));
        assertEquals(1500L, vo.getTurnoverList().get(2), "今天应有营业额");
        assertEquals(vo.getDateList().get(2), today.toString());
    }

    @Test
    @DisplayName("Top10：按快照名聚合销量降序")
    void top10AggregatesByName() {
        LocalDate today = LocalDate.now();
        newOrder(StatusConstants.Order.COMPLETED, 1000, "热销A", 2, 0);
        newOrder(StatusConstants.Order.COMPLETED, 1000, "热销A", 3, 0);
        newOrder(StatusConstants.Order.COMPLETED, 1000, "热销B", 1, 0);

        List<ReportTopVO> top = reportService.top10(today.minusDays(1), today, SHOP_ID);

        assertEquals("热销A", top.get(0).getName());
        assertEquals(5L, top.get(0).getCount());
        assertTrue(top.stream().anyMatch(t -> "热销B".equals(t.getName())));
    }

    @Test
    @DisplayName("区间护栏：begin>end 拒绝、超 92 天拒绝")
    void rangeGuards() {
        LocalDate today = LocalDate.now();
        assertThrows(BusinessException.class,
                () -> reportService.overview(today, today.minusDays(1), null));
        assertThrows(BusinessException.class,
                () -> reportService.overview(today.minusDays(100), today, null));
    }
}
