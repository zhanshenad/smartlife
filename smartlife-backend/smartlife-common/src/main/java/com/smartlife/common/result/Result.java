package com.smartlife.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 * 约定：code=1 成功，code=0 失败。
 * 前端所有接口都按这个结构取值，改动会连带改前端，不要单独调整。
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 1;
    public static final int FAIL = 0;

    private Integer code;
    private String msg;
    private T data;

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, null, data);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(FAIL, msg, null);
    }

    public static <T> Result<T> fail(String msg, T data) {
        return new Result<>(FAIL, msg, data);
    }

    public static <T> Result<T> build(Integer code, String msg, T data) {
        return new Result<>(code, msg, data);
    }

    /**
     * 按条件返回成功或失败，省掉调用方的一层 if。
     */
    public static <T> Result<T> by(boolean success, String failMsg) {
        return success ? ok() : fail(failMsg);
    }
}
