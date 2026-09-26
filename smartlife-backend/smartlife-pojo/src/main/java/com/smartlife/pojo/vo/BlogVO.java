package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.Blog;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 笔记详情视图：附作者信息与当前用户点赞状态 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BlogVO extends Blog {

    private String nickName;

    private String icon;

    /** 当前用户是否已点赞，未登录为 false */
    private Boolean isLike;
}
