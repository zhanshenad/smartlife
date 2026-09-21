package com.smartlife.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 * 继承 BaseMapper 即自带单表 CRUD，绝大多数场景不需要手写 XML。
 * 复杂查询（多表关联、统计报表）再补自定义方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
