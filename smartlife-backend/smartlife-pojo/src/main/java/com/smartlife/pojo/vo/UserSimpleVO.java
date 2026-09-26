package com.smartlife.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/** 用户简要信息：点赞排行榜、共同关注等列表场景复用，不暴露手机号等敏感字段 */
@Data
public class UserSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String nickName;

    private String icon;
}
