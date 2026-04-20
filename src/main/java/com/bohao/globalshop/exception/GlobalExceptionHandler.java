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

    /**
     * 处理空指针异常（最具体的异常类型，优先级最高）
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<String> handleNullPointerException(NullPointerException e) {
        log.error("触发空指针异常: ", e);
        return Result.error(500, "后端代码出现空指针异常，请查看 IDEA 控制台排查！");
    }

    /**
     * 处理运行时异常（业务异常）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("触发业务异常: ", e);
        return Result.error(500, e.getMessage());
    }

    /**
     * 处理其他未知异常（最宽泛的异常类型，优先级最低）
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("触发未知系统异常:", e);
        return Result.error(500, "哎呀，服务器开小差了，请稍后再试！");
    }
}
