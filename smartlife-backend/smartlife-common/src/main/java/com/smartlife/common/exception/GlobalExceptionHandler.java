package com.smartlife.common.exception;

import com.smartlife.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常兜底：业务异常的消息直接返给前端，其余异常只回统一话术，堆栈进日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** BusinessException 继承 BaseException，父类 handler 一并覆盖两者 */
    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /** @Valid 校验失败：把所有字段错误拼成一句话，前端一次提示完整 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", msg);
        return Result.fail(msg.isEmpty() ? "参数校验失败" : msg);
    }

    /** 唯一索引冲突兜底。业务层应自行捕获以给出更精确的提示（如"您已领取过该券"） */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突：{}", e.getMessage());
        return Result.fail("数据已存在，请勿重复提交");
    }

    /** 单个请求参数上的校验注解失败时走这里，如发码接口的手机号 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", msg);
        return Result.fail(msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("服务器开小差了，请稍后重试");
    }
}
