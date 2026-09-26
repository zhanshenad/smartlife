package com.smartlife.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 发布探店笔记入参 */
@Data
public class BlogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "店铺不能为空")
    private Long shopId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题最长 255 字")
    private String title;

    /** 最多 9 张，英文逗号分隔 */
    @NotBlank(message = "图片不能为空")
    @Size(max = 2048, message = "图片列表过长")
    private String images;

    @NotBlank(message = "正文不能为空")
    @Size(max = 2048, message = "正文最长 2048 字")
    private String content;
}
