package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.pojo.entity.Orders;
import com.smartlife.pojo.vo.ReportTopVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    /** 区间营业额（分）：已完成订单实付合计。shopId 为 null 表示全平台 */
    @Select("<script>SELECT IFNULL(SUM(pay_amount), 0) FROM tb_orders " +
            "WHERE status = " + StatusConstants.Order.COMPLETED + " AND order_time BETWEEN #{begin} AND #{end} " +
            "<if test='shopId != null'>AND shop_id = #{shopId}</if></script>")
    Long sumTurnover(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
                     @Param("shopId") Long shopId);

    /** 区间订单总数与已完成数：{total, valid} 两行口径一条 SQL 出 */
    @Select("<script>SELECT COUNT(*) AS total, IFNULL(SUM(status = " + StatusConstants.Order.COMPLETED +
            "), 0) AS valid FROM tb_orders " +
            "WHERE order_time BETWEEN #{begin} AND #{end} " +
            "<if test='shopId != null'>AND shop_id = #{shopId}</if></script>")
    Map<String, Object> countOrders(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
                                    @Param("shopId") Long shopId);

    /** 按日聚合营业额/订单数：只返回有订单的日子，Java 侧对齐日期轴补零 */
    @Select("<script>SELECT DATE(order_time) AS day, IFNULL(SUM(pay_amount), 0) AS turnover, COUNT(*) AS cnt " +
            "FROM tb_orders WHERE status = " + StatusConstants.Order.COMPLETED +
            " AND order_time BETWEEN #{begin} AND #{end} " +
            "<if test='shopId != null'>AND shop_id = #{shopId}</if> " +
            "GROUP BY DATE(order_time)</script>")
    List<Map<String, Object>> turnoverByDay(@Param("begin") LocalDateTime begin,
                                            @Param("end") LocalDateTime end,
                                            @Param("shopId") Long shopId);

    /** 热销 Top N：已完成订单明细按下单时快照名聚合销量 */
    @Select("<script>SELECT od.name AS name, SUM(od.number) AS count FROM tb_order_detail od " +
            "JOIN tb_orders o ON od.order_id = o.id " +
            "WHERE o.status = " + StatusConstants.Order.COMPLETED + " AND o.order_time BETWEEN #{begin} AND #{end} " +
            "<if test='shopId != null'>AND o.shop_id = #{shopId}</if> " +
            "GROUP BY od.name ORDER BY count DESC LIMIT #{limit}</script>")
    List<ReportTopVO> topProducts(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
                                  @Param("shopId") Long shopId, @Param("limit") int limit);
}
