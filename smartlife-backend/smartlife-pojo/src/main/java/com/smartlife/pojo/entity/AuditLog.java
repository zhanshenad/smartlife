package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端操作审计日志。只增不改，用于回答"谁在什么时候批了谁、封了谁"。
 * detail 存变更前后的 JSON 快照。不存快照的话，出了纠纷只能看到"改过"，
 * 看不到"从什么改成什么"。
 */
@Data
@TableName("tb_audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人（管理员）id */
    private Long operatorId;

    /** 冗余操作人名称：管理员账号可能被删，日志里的署名要留存 */
    private String operatorName;

    /** 动作标识，如 APPROVE_MERCHANT / BAN_USER / KICK_SESSION */
    private String action;

    /** 目标对象类型，如 USER / SHOP / VOUCHER / MERCHANT_APPLY */
    private String targetType;

    private Long targetId;

    /** 变更明细，JSON 格式 */
    private String detail;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
