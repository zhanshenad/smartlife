package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /** 核销（CAS）：WHERE 带当前状态，并发核销只有一个赢家 */
    @Update("UPDATE tb_voucher_order SET status = 2, use_time = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId} AND status = 1")
    int casUse(@Param("id") Long id, @Param("userId") Long userId);

    /** 退券（CAS）：订单取消/拒单时把核销的券还回去，与核销对称 */
    @Update("UPDATE tb_voucher_order SET status = 1, use_time = NULL, update_time = NOW() " +
            "WHERE id = #{id} AND status = 2")
    int casRestore(@Param("id") Long id);
}
