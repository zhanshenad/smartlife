package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.dto.SeckillMessage;
import com.smartlife.pojo.entity.VoucherOrder;
import com.smartlife.pojo.vo.VoucherOrderVO;

import java.util.List;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 普通券领取：Redis Set 预检 + 同步落库，落库失败回滚 Set（§5.2.5） */
    void grabVoucher(Long voucherId);

    /** 秒杀抢券：Lua 预检（含时间窗）+ 发 MQ，返回 orderId（此时订单尚未落库） */
    Long seckillVoucher(Long voucherId);

    /** MQ 消费者落库：幂等判重 + 扣 DB 库存；DB 库存扣不动则回滚订单并回补 Redis（§5.2.6.1） */
    void handleSeckillMessage(SeckillMessage msg);

    /** 我的券包（带券资料快照） */
    List<VoucherOrderVO> listMy();
}
