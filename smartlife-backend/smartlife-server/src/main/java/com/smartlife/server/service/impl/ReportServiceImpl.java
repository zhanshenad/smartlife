package com.smartlife.server.service.impl;

import com.smartlife.common.exception.BusinessException;
import com.smartlife.pojo.vo.ReportOverviewVO;
import com.smartlife.pojo.vo.ReportTopVO;
import com.smartlife.pojo.vo.ReportTrendVO;
import com.smartlife.server.mapper.OrderMapper;
import com.smartlife.server.mapper.UserMapper;
import com.smartlife.server.service.IReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板实现。趋势的日期轴由 Java 补全：DB 只返回有数据的日子，
 * 空档补零，前端拿到连续序列（苍穹报表同款做法）。
 */
@Service
public class ReportServiceImpl implements IReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_RANGE_DAYS = 92;

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    public ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
    }

    @Override
    public ReportOverviewVO overview(LocalDate begin, LocalDate end, Long shopId) {
        checkRange(begin, end);
        LocalDateTime from = begin.atStartOfDay();
        LocalDateTime to = end.atTime(23, 59, 59);

        ReportOverviewVO vo = new ReportOverviewVO();
        vo.setTurnover(orderMapper.sumTurnover(from, to, shopId));
        Map<String, Object> counts = orderMapper.countOrders(from, to, shopId);
        long total = toLong(counts.get("total"));
        long valid = toLong(counts.get("valid"));
        vo.setOrderCount(total);
        vo.setValidOrderCount(valid);
        vo.setOrderCompletionRate(total == 0 ? 0.0 : Math.round(valid * 1000.0 / total) / 1000.0);
        // 用户是平台资产，店铺维度没有这个口径
        vo.setUserCount(shopId == null
                ? userMapper.newUsersByDay(from, to).stream().mapToLong(m -> toLong(m.get("cnt"))).sum()
                : 0L);
        return vo;
    }

    @Override
    public ReportTrendVO trend(LocalDate begin, LocalDate end, Long shopId) {
        checkRange(begin, end);
        LocalDateTime from = begin.atStartOfDay();
        LocalDateTime to = end.atTime(23, 59, 59);

        Map<String, Long> turnoverMap = new HashMap<>();
        Map<String, Long> orderCountMap = new HashMap<>();
        for (Map<String, Object> row : orderMapper.turnoverByDay(from, to, shopId)) {
            String key = toDayKey(row.get("day"));
            turnoverMap.put(key, toLong(row.get("turnover")));
            orderCountMap.put(key, toLong(row.get("cnt")));
        }
        Map<String, Long> userMap = new HashMap<>();
        if (shopId == null) {
            for (Map<String, Object> row : userMapper.newUsersByDay(from, to)) {
                userMap.put(toDayKey(row.get("day")), toLong(row.get("cnt")));
            }
        }

        ReportTrendVO vo = new ReportTrendVO();
        List<String> dates = new ArrayList<>();
        List<Long> turnovers = new ArrayList<>();
        List<Long> orders = new ArrayList<>();
        List<Long> users = new ArrayList<>();
        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.format(DATE_FMT);
            dates.add(key);
            turnovers.add(turnoverMap.getOrDefault(key, 0L));
            orders.add(orderCountMap.getOrDefault(key, 0L));
            users.add(userMap.getOrDefault(key, 0L));
        }
        vo.setDateList(dates);
        vo.setTurnoverList(turnovers);
        vo.setOrderList(orders);
        vo.setUserList(users);
        return vo;
    }

    @Override
    public List<ReportTopVO> top10(LocalDate begin, LocalDate end, Long shopId) {
        checkRange(begin, end);
        return orderMapper.topProducts(begin.atStartOfDay(), end.atTime(23, 59, 59), shopId, 10);
    }

    /** begin>end 抛异常防死循环（§4.3 #31），区间封顶 92 天防全表扫 */
    private void checkRange(LocalDate begin, LocalDate end) {
        if (begin == null || end == null) {
            throw new BusinessException("起止日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (begin.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BusinessException("查询区间最多 " + MAX_RANGE_DAYS + " 天");
        }
    }

    /** DB 聚合列可能是 Long/BigInteger/BigDecimal，统一转 long */
    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** DATE() 列回来是 java.sql.Date 或 LocalDate，统一成 yyyy-MM-dd 键 */
    private String toDayKey(Object day) {
        return day == null ? "" : day.toString();
    }
}
