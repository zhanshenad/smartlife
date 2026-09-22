package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.Shop;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附近店铺的返回视图，在店铺字段外多带一个距离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopVO extends Shop {

    private static final long serialVersionUID = 1L;

    /** 距查询点距离，单位米 */
    private Double distance;
}
