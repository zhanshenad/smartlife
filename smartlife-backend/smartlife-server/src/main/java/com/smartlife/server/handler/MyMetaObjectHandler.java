package com.smartlife.server.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.smartlife.common.context.BaseContext;
import com.smartlife.common.model.LoginUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * create_time / update_time / create_user / update_user 的自动填充。
 * 用 strictXxxFill 而非 setFieldValByName：前者只在字段为 null 时填，不会覆盖业务显式设的值。
 * 它们只在实体字段标了 @TableField(fill = ...) 时才生效，漏标则静默不填。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        Long userId = currentUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createUser", Long.class, userId);
            this.strictInsertFill(metaObject, "updateUser", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        Long userId = currentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateUser", Long.class, userId);
        }
    }

    /** 取当前登录用户。定时任务、MQ 消费者等场景没有上下文，返回 null 时跳过填充而非中断写入 */
    private Long currentUserId() {
        LoginUser user = BaseContext.get();
        return user == null ? null : user.getId();
    }
}
