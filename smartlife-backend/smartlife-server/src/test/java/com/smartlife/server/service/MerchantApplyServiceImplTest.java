package com.smartlife.server.service;

import com.smartlife.common.constant.RedisConstants;
import com.smartlife.common.constant.RoleConstants;
import com.smartlife.common.constant.StatusConstants;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.MerchantApplyDTO;
import com.smartlife.pojo.entity.MerchantApply;
import com.smartlife.pojo.entity.Shop;
import com.smartlife.pojo.entity.User;
import com.smartlife.server.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入驻审核测试。approve 是多表写入（申请单+店铺+用户角色），
 * 用 NOT_SUPPORTED 挂起测试事务让生产路径的事务真正生效，验证完手工清。
 */
@SpringBootTest
@DisplayName("入驻审核：提交/建店/驳回")
class MerchantApplyServiceImplTest {

    private static final Long ADMIN_ID = 996L;
    private static final Double X = 123.43;
    private static final Double Y = 41.80;

    @Autowired
    private IMerchantApplyService applyService;
    @Autowired
    private IShopService shopService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redis;

    private User applicant;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    /** 按 id 重读最新申请单再清（建店发生在 approve 之后，老快照里 shopId 是 null） */
    private void cleanAll(Long applyId) {
        MerchantApply apply = applyId == null ? null : applyService.getById(applyId);
        if (apply != null) {
            if (apply.getShopId() != null) {
                // GEO 是全类共享 key，只能摘自己的成员，不能整 key 删
                redis.opsForZSet().remove(RedisConstants.SHOP_GEO_KEY + apply.getShopTypeId(),
                        apply.getShopId().toString());
                shopService.removeById(apply.getShopId());
            }
            applyService.removeById(apply.getId());
        }
        if (applicant != null && applicant.getId() != null) {
            userMapper.deleteById(applicant.getId());
        }
    }

    private User newUser() {
        User u = new User();
        u.setPhone("138" + String.format("%08d", System.nanoTime() % 100000000L));
        u.setNickName("入驻申请人");
        u.setRole(RoleConstants.USER);
        u.setStatus(StatusConstants.Common.ENABLED);
        userMapper.insert(u);
        return u;
    }

    private MerchantApplyDTO dto() {
        MerchantApplyDTO dto = new MerchantApplyDTO();
        dto.setShopName("测试新店");
        dto.setShopTypeId(1L);
        dto.setArea("辽宁省沈阳市");
        dto.setAddress("浑南区创新路195号");
        dto.setX(X);
        dto.setY(Y);
        dto.setContactName("张三");
        dto.setContactPhone("13800000000");
        return dto;
    }

    private MerchantApply submitAs(User user) {
        BaseContext.set(new LoginUser(user.getId(), user.getRole(), user.getNickName()));
        return applyService.getById(applyService.submit(dto()));
    }

    private void loginAsAdmin() {
        BaseContext.set(new LoginUser(ADMIN_ID, RoleConstants.ADMIN, "测试管理员"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("通过：CAS 占位 → 建店回填 → 升角色 → 进 GEO 索引，一条龙同事务")
    void approveCreatesShopAndUpgradesRole() {
        applicant = newUser();
        MerchantApply apply = submitAs(applicant);
        loginAsAdmin();
        try {
            applyService.approve(apply.getId());

            MerchantApply after = applyService.getById(apply.getId());
            assertEquals(StatusConstants.MerchantApply.APPROVED, after.getStatus());
            assertNotNull(after.getShopId());
            assertEquals(RoleConstants.MERCHANT,
                    userMapper.selectById(applicant.getId()).getRole());
            Shop shop = shopService.getById(after.getShopId());
            assertEquals(applicant.getId(), shop.getMerchantId());
            assertEquals("测试新店", shop.getName());
            // GEO 底层是 ZSet：member 存在即已入索引
            assertNotNull(redis.opsForZSet().score(
                    RedisConstants.SHOP_GEO_KEY + 1L, after.getShopId().toString()),
                    "新店应进入 GEO 索引");
        } finally {
            cleanAll(apply.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("重复审核：第二次 approve 被拒（CAS 幂等）")
    void approveTwiceRejected() {
        applicant = newUser();
        MerchantApply apply = submitAs(applicant);
        loginAsAdmin();
        try {
            applyService.approve(apply.getId());
            BusinessException e = assertThrows(BusinessException.class,
                    () -> applyService.approve(apply.getId()));
            assertTrue(e.getMessage().contains("已被处理"));
        } finally {
            cleanAll(apply.getId());
        }
    }

    @Test
    @Transactional
    @DisplayName("驳回：意见必填，状态置 2 且不建店")
    void rejectRequiresRemark() {
        applicant = newUser();
        MerchantApply apply = submitAs(applicant);
        loginAsAdmin();
        try {
            assertThrows(BusinessException.class,
                    () -> applyService.reject(apply.getId(), " "));

            applyService.reject(apply.getId(), "资质不全");
            assertEquals(StatusConstants.MerchantApply.REJECTED,
                    applyService.getById(apply.getId()).getStatus());
            assertNull(applyService.getById(apply.getId()).getShopId());
            assertEquals(RoleConstants.USER, userMapper.selectById(applicant.getId()).getRole());
        } finally {
            cleanAll(apply.getId());
        }
    }

    @Test
    @Transactional
    @DisplayName("提交护栏：pending 期间重复提交拒、已有店铺拒")
    void submitGuards() {
        applicant = newUser();
        BaseContext.set(new LoginUser(applicant.getId(), RoleConstants.USER, applicant.getNickName()));
        MerchantApply first = submitAs(applicant);
        try {
            BusinessException e1 = assertThrows(BusinessException.class,
                    () -> applyService.submit(dto()));
            assertTrue(e1.getMessage().contains("待审核"));

            // 造一个已有店的情况：直接驳回第一单后人为给用户挂店（借现有测试店）
            applyService.reject(first.getId(), "撤回");
            Shop existing = new Shop();
            existing.setMerchantId(applicant.getId());
            existing.setName("已存在的店");
            existing.setTypeId(1L);
            existing.setArea("辽宁省沈阳市");
            existing.setAddress("浑南区创新路195号");
            existing.setX(X);
            existing.setY(Y);
            existing.setStatus(StatusConstants.Common.ENABLED);
            shopService.save(existing);
            try {
                BusinessException e2 = assertThrows(BusinessException.class,
                        () -> applyService.submit(dto()));
                assertTrue(e2.getMessage().contains("已是商家"));
            } finally {
                shopService.removeById(existing.getId());
            }
        } finally {
            cleanAll(first.getId());
        }
    }

    @Test
    @Transactional
    @DisplayName("我的申请：只看到自己最近一条")
    void myLatestReturnsMine() {
        applicant = newUser();
        MerchantApply mine = submitAs(applicant);
        try {
            MerchantApply latest = applyService.myLatest();
            assertEquals(mine.getId(), latest.getId());
            assertEquals(applicant.getId(), latest.getUserId());
        } finally {
            cleanAll(mine.getId());
        }
    }
}
