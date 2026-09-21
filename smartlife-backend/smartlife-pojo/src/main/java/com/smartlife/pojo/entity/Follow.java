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
 * 关注关系。
 * 关系数据其实全在 Redis Set 里（follow:{userId}），本表是持久化落点——
 * Redis 重启后要能从库里重建，不能让关注关系凭空消失。
 */
@Data
@TableName("tb_follow")
public class Follow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关注发起方 */
    private Long userId;

    /** 被关注的人 */
    private Long followUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
