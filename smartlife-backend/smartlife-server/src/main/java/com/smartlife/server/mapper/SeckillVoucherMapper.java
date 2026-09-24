package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * DB 库存扣减（乐观锁）：消费者落库时调用，影响 0 行说明 Redis 与 DB 已漂移。
     * 与 tb_voucher_id 精确匹配，stock > 0 才扣。
     */
    @Update("UPDATE tb_seckill_voucher SET stock = stock - 1, update_time = NOW() " +
            "WHERE voucher_id = #{voucherId} AND stock > 0")
    int deductStock(@Param("voucherId") Long voucherId);
}
