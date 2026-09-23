package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.OrderDetail;
import com.smartlife.pojo.entity.Orders;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 订单出参：列表带店铺名，详情带明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVO extends Orders {

    private String shopName;

    /** 订单明细，仅详情接口填充 */
    private List<OrderDetail> detailList;
}
