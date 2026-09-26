package com.smartlife.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 博客评论入参：parentId=0 为一级评论，否则为对某条一级评论的回复 */
@Data
public class CommentsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long parentId;

    /** 被回复的评论 id，仅二级评论填写 */
    private Long answerId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 255, message = "评论最长 255 字")
    private String content;
}
