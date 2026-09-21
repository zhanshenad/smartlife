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
 * 商家入驻申请单。审核通过后自动建店，并把 shopId 回填到本单。
 * 幂等要点：审核只允许发生在 status = 0 的申请单上，重复审核直接拒绝——
 * 否则同一张申请单可能建出两家店。
 */
@Data
@TableName("tb_merchant_apply")
public class MerchantApply implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 申请人（role=2 的用户） */
    private Long userId;

    private String shopName;

    private Long shopTypeId;

    private String area;

    private String address;

    private Double x;

    private Double y;

    private String contactName;

    private String contactPhone;

    /** 营业执照等资质图，多张逗号分隔 */
    private String licenseImages;

    /** 0 待审核 1 已通过 2 已驳回 */
    private Integer status;

    /** 审核意见（驳回时必填） */
    private String auditRemark;

    private Long auditUserId;

    private LocalDateTime auditTime;

    /** 审核通过后创建的店铺 id，未通过为 null */
    private Long shopId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
