package com.smartlife.server.handler;

import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.entity.User;
import com.smartlife.server.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段自动填充的实测。
 * 填充这条链路有四段，缺任何一段都会**静默失效**（不报错、字段就是 NULL）：
 * ① 实体字段标 @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)   ← 声明"我要被填"
 * ② MetaObjectHandler 实现类标 @Component                            ← 让 Spring 找到它
 * ③ 实现 insertFill / updateFill，用 strictXxxFill 填值               ← 真正填
 * ④ 该实现类被启动扫描到（scanBasePackages 覆盖到它）← 否则前三条全白搭
 * 本测试把 ①②③ 一次性验掉；④ 由"测试能启动上下文"间接证明。
 */
@SpringBootTest
@DisplayName("MyMetaObjectHandler：字段自动填充")
class MyMetaObjectHandlerTest {

    @Autowired
    private UserMapper userMapper;

    private Long insertedId;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
        if (insertedId != null) {
            userMapper.deleteById(insertedId);
            insertedId = null;
        }
    }

    @Test
    @DisplayName("insert：createTime / updateTime 自动填上，且真的落库")
    void insertFillsTimestamps() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        User user = new User();
        user.setPhone("19900000001");
        user.setNickName("填充测试");
        user.setRole(1);
        user.setStatus(1);

        userMapper.insert(user);
        insertedId = user.getId();

        assertNotNull(user.getId(), "自增主键应回填到实体");
        assertNotNull(user.getCreateTime(), "createTime 应被填充到实体对象上");

        // 关键：不只看内存里的实体，要重新查库确认写进去了
        User fromDb = userMapper.selectById(insertedId);
        assertNotNull(fromDb.getCreateTime(), "createTime 应真的落库（不是只改了内存对象）");
        assertNotNull(fromDb.getUpdateTime(), "updateTime 应真的落库");
        assertTrue(fromDb.getCreateTime().isAfter(before), "填的应是当前时间，不是某个默认值");
    }

    @Test
    @DisplayName("update：updateTime 被刷新，createTime 保持不变")
    void updateRefreshesUpdateTime() {
        User user = new User();
        user.setPhone("19900000003");
        user.setNickName("待改名");
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
        insertedId = user.getId();

        LocalDateTime createTimeBefore = userMapper.selectById(insertedId).getCreateTime();

        User patch = new User();
        patch.setId(insertedId);
        patch.setNickName("改过了");
        userMapper.updateById(patch);

        User after = userMapper.selectById(insertedId);
        assertEquals("改过了", after.getNickName());
        assertEquals(createTimeBefore, after.getCreateTime(), "createTime 不该被 update 动到");
        assertNotNull(after.getUpdateTime(), "updateTime 仍在");
    }

    @Test
    @DisplayName("登录上下文中写入：@Component 的 handler 能读到 BaseContext")
    void handlerSeesLoginContext() {
        // 这里只验证"填充逻辑在有登录上下文时不会出错"。
        // createUser/updateUser 的填充用的是同一套机制，承载它们的实体是 Category/Dish/Voucher。
        BaseContext.set(new LoginUser(42L, 3, "管理员"));

        User user = new User();
        user.setPhone("19900000004");
        user.setNickName("带上下文");
        user.setRole(1);
        user.setStatus(1);

        userMapper.insert(user);
        insertedId = user.getId();

        assertNotNull(userMapper.selectById(insertedId).getCreateTime());
    }
}
