package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /** 乐观锁扣库存：stock >= n 才扣，返回 0 行表示库存不足 */
    @Update("UPDATE tb_dish SET stock = stock - #{n} WHERE id = #{id} AND stock >= #{n}")
    int deductStock(@Param("id") Long id, @Param("n") int n);

    /** 回补库存：用户取消 / 商家拒单时把扣掉的还回去 */
    @Update("UPDATE tb_dish SET stock = stock + #{n} WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("n") int n);
}
