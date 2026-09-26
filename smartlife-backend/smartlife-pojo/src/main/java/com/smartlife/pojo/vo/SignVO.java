package com.smartlife.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 签到信息视图：本月第几天签了（1 起），加连续签到天数 */
@Data
public class SignVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本月签到日期（几号），升序 */
    private List<Integer> signDays;

    /** 连续签到天数，今天未签则从昨天往前数 */
    private int continuousDays;
}
