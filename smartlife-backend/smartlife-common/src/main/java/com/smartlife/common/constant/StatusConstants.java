package com.smartlife.common.constant;

/**
 * 各类业务状态码。全部集中在此，禁止在 Service 里写魔法数字。
 */
public class StatusConstants {

    private StatusConstants() {
    }

    /** 通用启停（菜品 / 套餐 / 分类 / 账号 共用） */
    public static class Common {
        private Common() {
        }

        public static final int DISABLED = 0;
        public static final int ENABLED = 1;
    }

    /** 订单状态，对应 tb_orders.status（§6.2 第 4 条） */
    public static class Order {
        private Order() {
        }

        public static final int PENDING_PAYMENT = 1;
        public static final int TO_BE_CONFIRMED = 2;
        public static final int CONFIRMED = 3;
        public static final int DELIVERY_IN_PROGRESS = 4;
        public static final int COMPLETED = 5;
        public static final int CANCELLED = 6;
    }

    /** 支付状态，对应 tb_orders.pay_status */
    public static class Pay {
        private Pay() {
        }

        public static final int UN_PAID = 0;
        public static final int PAID = 1;
        public static final int REFUND = 2;
    }

    /** 券类型，对应 tb_voucher.type（§5.2.1） */
    public static class VoucherType {
        private VoucherType() {
        }

        /** 普通券：无库存，一人一张，同步落库 */
        public static final int NORMAL = 0;

        /** 秒杀券：有库存，Lua 预扣 + MQ 异步落库 */
        public static final int SECKILL = 1;
    }

    /** 券的上下架状态，对应 tb_voucher.status */
    public static class Voucher {
        private Voucher() {
        }

        public static final int ON_SHELF = 1;
        public static final int OFF_SHELF = 2;
        public static final int EXPIRED = 3;
    }

    /**
     * 券订单状态，对应 tb_voucher_order.status。
     * 这是"券用了没有"，与券库存是两回事（§5.2.4）。
     * 本项目是领券模型（§5.2.2），只有未使用与已使用两个状态，不要照搬买券模型的三态。
     */
    public static class VoucherOrder {
        private VoucherOrder() {
        }

        /** 已领取 */
        public static final int UNUSED = 1;

        /** 已核销。流转靠乐观锁 CAS：WHERE status = 1 */
        public static final int USED = 2;
    }

    /** 入驻申请状态，对应 tb_merchant_apply.status */
    public static class MerchantApply {
        private MerchantApply() {
        }

        /** 待审核。审核动作只允许发生在该状态上（CAS 幂等） */
        public static final int PENDING = 0;

        /** 已通过（同事务建店并回填 shopId） */
        public static final int APPROVED = 1;

        /** 已驳回 */
        public static final int REJECTED = 2;
    }

    /** 评论状态，对应 tb_blog_comments.status */
    public static class Comment {
        private Comment() {
        }

        /** 一级评论的 parentId 哨兵值（二级评论挂一级下，最多两级） */
        public static final long TOP_LEVEL = 0L;

        public static final int NORMAL = 0;
        public static final int REPORTED = 1;
        public static final int BLOCKED = 2;
    }

    /**
     * 秒杀 Lua 返回码，必须与 lua/seckill.lua 一一对应。
     * 本项目比参考实现多了 3/4（时间窗），对齐时最容易漏的是 -1。
     */
    public static class SeckillCode {
        private SeckillCode() {
        }

        /** 库存 key 不存在（未预热） */
        public static final long STOCK_NOT_READY = -1L;
        public static final long SUCCESS = 0L;
        public static final long OUT_OF_STOCK = 1L;
        public static final long REPEAT_ORDER = 2L;
        public static final long NOT_STARTED = 3L;
        public static final long ENDED = 4L;
    }
}
