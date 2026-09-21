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
 * 探店笔记。
 * liked 是展示用的计数，权威数据在 Redis ZSet blog:liked:{blogId} 里
 * （member=userId，score=点赞时间）。ZSet 同时承担两个职责：判重（是否已赞）+ 点赞排行榜（按时间排序）。
 */
@Data
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long userId;

    private String title;

    /** 最多 9 张，英文逗号分隔 */
    private String images;

    private String content;

    /** 点赞数（展示计数，权威在 Redis ZSet） */
    private Integer liked;

    private Integer comments;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
