package com.smartlife.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 收件箱滚动分页结果：minTime 为下次 lastId，offset 为需跳过的同分条数
 * （score 唯一时恒 1，机制防御性保留）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;

    /** 下次请求的 lastId */
    private Long minTime;

    /** 下次查询需跳过的同分条数 */
    private Integer offset;
}
