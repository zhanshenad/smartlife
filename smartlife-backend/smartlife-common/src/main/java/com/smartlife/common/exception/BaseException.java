package com.smartlife.common.exception;

import java.io.Serial;

/**
 * 异常体系根类。继承 RuntimeException（非受检），
 * 这样业务层可以直接抛出，不必在每层方法签名上挂 throws。
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
