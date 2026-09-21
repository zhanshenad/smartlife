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
 * 探店笔记的评论，两级结构：
 * 一级评论  parentId = 0
 * └─ 回复 parentId = 一级评论id, answerId = 被回复的那条评论id
 * 刻意不做无限层级——本地生活场景里三级以上没人看，前端展示也撑不住。
 */
@Data
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long blogId;

    /** 所属一级评论 id；一级评论本身为 0 */
    private Long parentId;

    /** 回复的目标评论 id */
    private Long answerId;

    private String content;

    private Integer liked;

    /** 0 正常 1 被举报 2 禁止查看 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
