package com.smartlife.server.service.impl;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.pojo.entity.Sign;
import com.smartlife.pojo.vo.SignVO;
import com.smartlife.server.mapper.SignMapper;
import com.smartlife.server.service.ISignService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 签到：Redis BitMap 为在线路径，tb_sign 为持久化落点。
 * 连续天数从今天往前数（今天未签从昨天数），本月全勤才跨月回看上月（仅一月）。
 */
@Service
public class SignServiceImpl implements ISignService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final SignMapper signMapper;
    private final StringRedisTemplate redis;

    public SignServiceImpl(SignMapper signMapper, StringRedisTemplate redis) {
        this.signMapper = signMapper;
        this.redis = redis;
    }

    @Override
    public SignVO sign() {
        Long userId = BaseContext.require().getId();
        LocalDate today = LocalDate.now();
        int day = today.getDayOfMonth();

        // 先 DB 后 Redis（对齐关注模块）：DB 故障时位图不被置位，
        // 否则次日重试会因 bit=1 跳过 DB 写入，造成 DB 永久缺记录
        Sign record = new Sign();
        record.setUserId(userId);
        record.setSignYear(today.getYear());
        record.setSignMonth(today.getMonthValue());
        record.setSignDate(today);
        record.setIsBackup(0);
        try {
            signMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 今日已签：幂等
        }
        // 重复置 1 无副作用，幂等
        redis.opsForValue().setBit(monthKey(userId, today), day - 1, true);
        return stats(userId, today);
    }

    @Override
    public SignVO mySign() {
        Long userId = BaseContext.require().getId();
        return stats(userId, LocalDate.now());
    }

    private SignVO stats(Long userId, LocalDate today) {
        String key = monthKey(userId, today);
        int day = today.getDayOfMonth();

        // 逐位读：bit i = 第 i+1 天
        List<Integer> signDays = new ArrayList<>();
        for (int i = 0; i < day; i++) {
            if (signed(key, i)) {
                signDays.add(i + 1);
            }
        }

        // 连续天数：今天未签则从昨天位开始严格连数
        int count = 0;
        int start = signed(key, day - 1) ? day - 1 : day - 2;
        for (int i = start; i >= 0; i--) {
            if (signed(key, i)) {
                count++;
            } else {
                break;
            }
        }
        // 本月至今全勤才跨月：从上月最后一天继续往前数
        if (count == day) {
            LocalDate prevEnd = today.withDayOfMonth(1).minusDays(1);
            String prevKey = monthKey(userId, prevEnd);
            for (int i = prevEnd.getDayOfMonth() - 1; i >= 0; i--) {
                if (signed(prevKey, i)) {
                    count++;
                } else {
                    break;
                }
            }
        }
        SignVO vo = new SignVO();
        vo.setSignDays(signDays);
        vo.setContinuousDays(count);
        return vo;
    }

    /** 逐位读：本机 Redis 的 BITFIELD 多位读与 SETBIT 位映射实测错位，单位读正常 */
    private boolean signed(String key, int offset) {
        return Boolean.TRUE.equals(redis.opsForValue().getBit(key, offset));
    }

    private String monthKey(Long userId, LocalDate month) {
        return RedisConstants.USER_SIGN_KEY + userId + ":" + month.format(MONTH_FMT);
    }
}
