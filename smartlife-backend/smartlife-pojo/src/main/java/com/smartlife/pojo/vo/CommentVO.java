package com.smartlife.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 评论视图：附评论者信息与二级回复列表 */
@Data
public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long parentId;

    /** 回复的目标评论 id，仅二级评论有 */
    private Long answerId;

    /** 回复目标的评论者昵称，仅二级评论有 */
    private String answerNickName;

    private String content;

    private Integer liked;

    private LocalDateTime createTime;

    private String nickName;

    private String icon;

    /** 二级回复，时间正序 */
    private List<CommentVO> children;
}
