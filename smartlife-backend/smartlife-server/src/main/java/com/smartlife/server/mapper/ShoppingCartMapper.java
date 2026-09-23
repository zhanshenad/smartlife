package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.ShoppingCart;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

    /**
     * 原子加购：不存在则插入(number=1)，存在则 number+1。
     * 依赖 uk_user_goods 唯一键；"查出来+1再写回"在并发下会丢更新，禁止。
     */
    @Insert("INSERT INTO tb_shopping_cart (user_id, name, image, dish_id, setmeal_id, dish_flavor, number, amount, create_time) " +
            "VALUES (#{userId}, #{name}, #{image}, #{dishId}, #{setmealId}, #{dishFlavor}, 1, #{amount}, NOW()) " +
            "ON DUPLICATE KEY UPDATE number = number + 1")
    int insertOrIncrement(ShoppingCart cart);

    /** 原子减购：number>1 时减 1，返回影响行数（0 表示已到 1，该走删除） */
    @Update("UPDATE tb_shopping_cart SET number = number - 1 " +
            "WHERE user_id = #{userId} AND dish_id = #{dishId} AND setmeal_id = #{setmealId} " +
            "AND dish_flavor = #{dishFlavor} AND number > 1")
    int decrement(@Param("userId") Long userId, @Param("dishId") Long dishId,
                  @Param("setmealId") Long setmealId, @Param("dishFlavor") String dishFlavor);
}
