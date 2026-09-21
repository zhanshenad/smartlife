package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表。三端共用一张表，靠 role 区分身份。
 */
@Data
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 微信登录标识，本项目未接微信，保留字段供扩展 */
    private String openid;

    private String phone;

    /**
     * BCrypt 加密存储，绝不明文。
     * @JsonIgnore 是兜底，不是正解：正解是接口一律返回 VO、不直接吐实体
     * （《重构计划》§3.3），P1 建出 UserVO 后实体的序列化就会自然消失。
     * 但兜底这层必须留着——万一哪天有人顺手 return Result.ok(user)，
     * 泄漏的是密码哈希，代价太大，不值得赌。
     */
    @JsonIgnore
    private String password;

    private String nickName;

    private String icon;

    /** 0 女 1 男 */
    private Integer sex;

    /** 1 用户端 2 商家端 3 管理端，见 RoleConstants */
    private Integer role;

    /** 0 禁用 1 启用。封禁时同步 INCR 会话版本号，使已签发的 token 立即失效 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
