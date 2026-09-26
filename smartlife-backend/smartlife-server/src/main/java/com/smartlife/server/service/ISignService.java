package com.smartlife.server.service;

import com.smartlife.pojo.vo.SignVO;

public interface ISignService {

    /** 今日签到（已签幂等），返回签到后的统计 */
    SignVO sign();

    /** 我的签到统计：本月记录 + 连续天数 */
    SignVO mySign();
}
