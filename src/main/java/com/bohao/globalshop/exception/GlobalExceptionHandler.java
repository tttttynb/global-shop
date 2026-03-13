package com.bohao.globalshop.exception;

import com.bohao.globalshop.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 作用：拦截系统运行中抛出的所有异常，并统一返回给前端友好的 JSON 格式
 */
@Slf4j
@RestControllerAdvice//自动监听所有 Controller 的动静
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("触发业务异常:{}", e.getMessage());
        return Result.error(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("触发未知系统异常:", e);
        return Result.error(500, "哎呀，服务器开小差了，请稍后再试！");
    }
}
