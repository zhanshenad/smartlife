package com.smartlife.server.service;

import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.vo.UserVO;

/** 管理端账号治理 */
public interface IAdminUserService {

    /** 分页查询全站账号，可按角色与状态过滤 */
    PageResult<UserVO> page(Integer role, Integer status, long current, long size);

    /** 封禁/解封。封禁同时踢掉全部在线会话，立即生效（§5.1.5 封禁闭环） */
    void changeStatus(Long userId, int status);
}
