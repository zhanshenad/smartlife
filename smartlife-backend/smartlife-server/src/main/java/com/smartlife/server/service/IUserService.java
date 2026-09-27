package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.pojo.dto.LoginDTO;
import com.smartlife.pojo.dto.PasswordDTO;
import com.smartlife.pojo.entity.User;
import com.smartlife.pojo.vo.LoginVO;

/**
 * 用户服务：验证码/密码双通道登录、设置密码。
 */
public interface IUserService extends IService<User> {

    /** 生成短信验证码并写入 Redis，未接短信通道时从日志取码 */
    void sendCode(String phone);

    /** 登录：code 非空走验证码通道（登录即注册），否则走密码通道（只登录不注册） */
    LoginVO login(LoginDTO dto);

    /** 设置/修改密码：首次设置免旧密；修改须验旧密并踢全端强制重登 */
    void setPassword(PasswordDTO dto);
}
