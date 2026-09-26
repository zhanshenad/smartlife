package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.vo.SignVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 签到：BitMap 读写幂等、连续天数（断签从昨天数、跨月回看）。
 * Redis 位图按测试用户 48 独立 key，AfterEach 清。
 */
@SpringBootTest
@Transactional
@DisplayName("签到：BitMap/连续天数")
class SignServiceImplTest {

    private static final Long USER_ID = 48L;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private ISignService signService;
    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        LocalDate today = LocalDate.now();
        redis.delete(monthKey(today));
        redis.delete(monthKey(today.withDayOfMonth(1).minusDays(1)));
    }

    private void loginAsUser() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

    private String monthKey(LocalDate month) {
        return RedisConstants.USER_SIGN_KEY + USER_ID + ":" + month.format(MONTH_FMT);
    }

    private void setBit(LocalDate date) {
        redis.opsForValue().setBit(monthKey(date), date.getDayOfMonth() - 1, true);
    }

    @Test
    @DisplayName("签到：连续 1 天，重复签幂等")
    void signTodayIdempotent() {
        loginAsUser();
        SignVO first = signService.sign();
        assertEquals(1, first.getContinuousDays());
        assertEquals(List.of(LocalDate.now().getDayOfMonth()), first.getSignDays());

        SignVO again = signService.sign();
        assertEquals(1, again.getContinuousDays(), "重复签到天数不变");
        assertEquals(1, again.getSignDays().size(), "本月仍只有一天");
    }

    @Test
    @DisplayName("断签：今天未签，从昨天往前数")
    void continuousSkipsUnsignedToday() {
        LocalDate today = LocalDate.now();
        setBit(today.minusDays(1));
        setBit(today.minusDays(2));

        loginAsUser();
        SignVO vo = signService.mySign();
        assertEquals(2, vo.getContinuousDays(), "昨天+前天连续，今天未签不算");
    }

    @Test
    @DisplayName("连续天数：中间断档即止")
    void continuousStopsAtGap() {
        LocalDate today = LocalDate.now();
        setBit(today.minusDays(1));
        setBit(today.minusDays(3));  // 前三天签了但中间断，不连

        loginAsUser();
        SignVO vo = signService.mySign();
        assertEquals(1, vo.getContinuousDays());
    }

    @Test
    @DisplayName("跨月：本月至今全勤则回看上月末尾")
    void continuousCrossesMonth() {
        LocalDate today = LocalDate.now();
        // 本月 1 号到今天全签
        for (int d = 1; d <= today.getDayOfMonth(); d++) {
            setBit(today.withDayOfMonth(d));
        }
        // 上月最后两天也签了
        LocalDate prevEnd = today.withDayOfMonth(1).minusDays(1);
        setBit(prevEnd);
        setBit(prevEnd.minusDays(1));

        loginAsUser();
        SignVO vo = signService.mySign();
        assertEquals(today.getDayOfMonth() + 2, vo.getContinuousDays());
    }
}
