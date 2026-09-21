package com.smartlife.common.exception;

import java.io.Serial;

/**
 * 业务异常：消息直接面向用户，会被 GlobalExceptionHandler 原样返回给前端。
 * 例：throw new BusinessException("库存不足");
 * 与之相对，技术性异常（空指针、SQL 错误等）走通用兜底，返回统一话术，
 * 不把堆栈细节暴露给客户端。
 */
public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
