package com.smartlife.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 签到记录。
 * 在线查询走 Redis BitMap（sign:{userId:{yyyyMM}}，第 n 位 = 当月第 n 天），本表用于持久化，
 * 保证 Redis 丢了也能恢复签到历史。
 * 字段刻意叫 signYear / signMonth / signDate 而不是裸的 year/month/date，
 * 避免与 MySQL 关键字和内置函数名撞车导致 SQL 需要额外转义。
 */
@Data
@TableName("tb_sign")
public class Sign implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer signYear;

    private Integer signMonth;

    private LocalDate signDate;

    /** 是否补签 */
    private Integer isBackup;
}
