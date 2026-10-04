package com.smartlife.common.constant;

/**
 * Redis key 前缀与 TTL 集中管理。
 * 约定：常量名以 _KEY 结尾的是 key 前缀，以 _TTL_MINUTES / _TTL_SECONDS
 * 结尾的是过期时间——单位写进名字里，避免"这个 30 到底是秒还是分"的歧义。
 */
public class RedisConstants {

    private RedisConstants() {
    }

    // ==================== 认证会话（§5.1.2） ====================

    /** 短信验证码 */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final long LOGIN_CODE_TTL_MINUTES = 2L;

    /**
     * 会话白名单，Hash：userId / role / nickname。
     * 这是身份的权威来源，拦截器不采信 JWT 里的同名 claim。
     * TTL 才是会话活性的唯一裁判，JWT 只负责"签名有效"。
     */
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    public static final long LOGIN_TOKEN_TTL_MINUTES = 30L;

    /** 会话 Hash 的字段名，登录写入与拦截器读取共用 */
    public static final String SESSION_FIELD_USER_ID = "userId";
    public static final String SESSION_FIELD_ROLE = "role";
    public static final String SESSION_FIELD_NICKNAME = "nickname";

    /** 该用户的 jti 集合，供"在线会话列表"查询（反向索引） */
    public static final String LOGIN_USER_KEY = "login:user:";
    public static final long LOGIN_USER_TTL_MINUTES = 30L;

    /** 会话版本号，踢全端用。绝不能设 TTL：过期归零会让旧 token 重新生效（§5.1.2） */
    public static final String LOGIN_VER_KEY = "login:ver:";

    /** 剩余 TTL 低于此阈值才续期，把"每请求一写"降为"每 15 分钟一写"（§5.1.4） */
    public static final long LOGIN_REFRESH_THRESHOLD_MINUTES = 15L;

    // ==================== 优惠券（§5.2） ====================

    /** 秒杀券库存 */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /** 秒杀券"一人一单"预检 Set，成员是 userId */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    /** 普通券"一人一张"预检 Set，成员是 userId */
    public static final String VOUCHER_ORDER_KEY = "voucher:order:";

    /** 秒杀对照组（SECKILL_LUA_ENABLED=false）的一人一单锁，按用户加锁 */
    public static final String SECKILL_LOCK_KEY = "lock:seckill:order:";

    // ==================== 防重复提交（§5.2.7 ⑤） ====================

    /** 下单防重复提交 token，按用户。秒杀有 Lua 挡重，普通下单没有，双击会各插一单 */
    public static final String ORDER_SUBMIT_TOKEN_KEY = "order:submit:token:";
    public static final long ORDER_SUBMIT_TOKEN_TTL_SECONDS = 5L;

    /** 普通券领取防重复提交 token，按 用户:券 组合（纯按用户会误伤连领不同券） */
    public static final String VOUCHER_GRAB_TOKEN_KEY = "voucher:grab:token:";
    public static final long VOUCHER_GRAB_TOKEN_TTL_SECONDS = 5L;

    // ==================== 缓存（§5.3） ====================

    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final long CACHE_SHOP_TTL_MINUTES = 30L;

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";
    public static final long CACHE_SHOP_TYPE_TTL_MINUTES = 30L;

    /** 菜品列表缓存，拼接 shopId:categoryId（GEOSEARCH 同理没法按条件过滤，分 key 隔离） */
    public static final String CACHE_DISH_KEY = "cache:dish:";
    public static final long CACHE_DISH_TTL_MINUTES = 30L;

    /** 套餐列表缓存，拼接 shopId:categoryId */
    public static final String CACHE_SETMEAL_KEY = "cache:setmeal:";
    public static final long CACHE_SETMEAL_TTL_MINUTES = 30L;

    /** 空值缓存，防穿透。TTL 取短，避免商家新建后长时间查不到 */
    public static final String CACHE_NULL_VALUE = "";
    public static final long CACHE_NULL_TTL_MINUTES = 2L;

    /** 互斥锁，防击穿。锁本身必须有 TTL，否则持锁线程崩溃即死锁（§5.3.1） */
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final long LOCK_SHOP_TTL_SECONDS = 10L;

    /** 逻辑过期重建锁，与互斥锁是两套用途，key 不复用（§5.3.1） */
    public static final String LOCK_REBUILD_KEY = "lock:rebuild:";
    public static final long LOCK_REBUILD_TTL_SECONDS = 10L;

    /** 启动缓存预热锁（Redisson），多实例部署时只放一个实例执行 */
    public static final String WARMUP_LOCK_KEY = "lock:warmup";

    public static final String CACHE_BLOG_KEY = "cache:blog:";

    // ==================== 地理位置 / 社交（§4.1） ====================

    /** 店铺 GEO，成员是 shopId */
    public static final String SHOP_GEO_KEY = "shop:geo:";

    /** 探店笔记点赞，ZSet：member=userId，score=点赞时间戳 */
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    /** 关注 Set：member=被关注者 userId（"我关注了谁"） */
    public static final String FOLLOW_KEY = "follow:";

    /** 粉丝 Set：member=粉丝 userId（"谁关注了我"），发笔记推送收件箱用 */
    public static final String FOLLOWERS_KEY = "follow:followers:";

    /** 关注流收件箱，ZSet：member=笔记id，score=笔记id（自增单调） */
    public static final String FEED_KEY = "feed:";

    /** 签到 BitMap，按年月分片：sign:{userId}:{yyyyMM} */
    public static final String USER_SIGN_KEY = "sign:";

    /** 店铺 UV 统计 HyperLogLog，按店铺按天分 key：uv:shop:{shopId}:{date} */
    public static final String UV_SHOP_KEY = "uv:shop:";

    // ==================== 全局唯一 ID（RedisIdWorker） ====================

    /** 订单号自增序列，按天分片：icr:order:{yyyy:MM:dd} */
    public static final String ORDER_ID_KEY = "icr:order:";

    /** 券订单号自增序列，按天分片：icr:voucherOrder:{yyyy:MM:dd} */
    public static final String VOUCHER_ORDER_ID_KEY = "icr:voucherOrder:";

    /** 探店笔记 ID 自增序列，按天分片：icr:blog:{yyyy:MM:dd} */
    public static final String BLOG_ID_KEY = "icr:blog:";

    /**
     * 自增序列键的 TTL。
     * 键本身按天分片（每天一个有界新键），这里再给 2 天 TTL，用完即回收，
     * 不让计数器在 Redis 里长期堆积。
     */
    public static final long ID_WORKER_TTL_DAYS = 2L;
}
