package com.smartlife.server.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 逻辑过期方案的缓存 value 包装：TTL 不交给 Redis 管，而是写在字段里。
 * 缓存 key 本身永不过期，读到的永远是"数据 + 它的逻辑到期时间"。
 */
@Data
public class RedisData implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 逻辑过期时间，过了它就该重建（但旧值照常返回） */
    private LocalDateTime expireTime;

    /** 业务数据，反序列化目标类型由调用方指定 */
    private Object data;
}
