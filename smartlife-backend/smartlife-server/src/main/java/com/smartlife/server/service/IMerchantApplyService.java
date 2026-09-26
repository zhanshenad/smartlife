package com.smartlife.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlife.common.result.PageResult;
import com.smartlife.pojo.dto.MerchantApplyDTO;
import com.smartlife.pojo.entity.MerchantApply;

/** 商家入驻申请：提交、审核（通过即同事务建店并升角色） */
public interface IMerchantApplyService extends IService<MerchantApply> {

    /** 提交申请。有待审单或已有店铺则拒 */
    Long submit(MerchantApplyDTO dto);

    /** 我最近一次申请（查进度用） */
    MerchantApply myLatest();

    /** 管理端分页，可按状态过滤 */
    PageResult<MerchantApply> page(Integer status, long current, long size);

    /** 审核通过：CAS 占位（仅待审单）→ 建店 → 回填 shopId → 升商家角色 */
    void approve(Long applyId);

    /** 驳回：CAS 占位 + 审核意见必填 */
    void reject(Long applyId, String remark);
}
