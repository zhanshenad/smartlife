package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.LoginVO;

/**
 * 用户服务：验证码发送与登录。
 */
public interface IUserService extends IService<User> {

    /** 生成短信验证码并写入 Redis，未接短信通道时从日志取码 */
    void sendCode(String phone);

    /** 验证码登录，查/建用户后签发 JWT 并写会话白名单 */
    LoginVO login(LoginDTO dto);
}
