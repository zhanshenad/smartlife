package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper。
 * 继承 BaseMapper 即自带单表 CRUD，绝大多数场景不需要手写 XML。
 * 复杂查询（多表关联、统计报表）再补自定义方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按日新增用户数：只返回有注册的日子，Java 侧对齐日期轴补零 */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS cnt FROM tb_user " +
            "WHERE create_time BETWEEN #{begin} AND #{end} GROUP BY DATE(create_time)")
    List<Map<String, Object>> newUsersByDay(@Param("begin") LocalDateTime begin,
                                            @Param("end") LocalDateTime end);
}
