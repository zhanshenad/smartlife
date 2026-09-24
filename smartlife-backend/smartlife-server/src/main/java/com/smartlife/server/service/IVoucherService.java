package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.dto.VoucherDTO;
import com.smartlife.pojo.entity.Voucher;
import com.smartlife.pojo.vo.VoucherVO;

import java.util.List;

public interface IVoucherService extends IService<Voucher> {

    /** 发券：普通券只写主表；秒杀券同事务写主表+附加表，提交后预热 Redis 库存 */
    void addVoucher(VoucherDTO dto);

    /** 用户端：某店上架中的券（秒杀券附库存与时间窗） */
    List<VoucherVO> listByShop(Long shopId);

    /** 商家端：本店全部券 */
    List<Voucher> listMyShop();
}
