package com.smartlife.server.service;

import com.smartlife.pojo.vo.BlogVO;
import com.smartlife.pojo.vo.ScrollResultVO;

public interface IFeedService {

    /**
     * 收件箱滚动分页。lastId 为空表示第一页（从头取）；
     * 之后传上页返回的 minTime 与 offset（跳过与 minTime 同分的条目，防重复）。
     */
    ScrollResultVO<BlogVO> scroll(Long lastId, Integer offset, int size);
}
