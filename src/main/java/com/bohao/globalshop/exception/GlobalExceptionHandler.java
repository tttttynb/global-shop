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
        // 核心修复：把 e 作为最后一个参数传进去，Slf4j 就会自动打印完整的红色报错行号！
        log.error("🚨 触发业务异常: ", e);

        // 如果是空指针，给前端一个友好的提示，而不是返回 null
        if (e instanceof NullPointerException) {
            return Result.error(500, "后端代码出现空指针异常，请查看 IDEA 控制台排查！");
        }
        return Result.error(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("触发未知系统异常:", e);
        return Result.error(500, "哎呀，服务器开小差了，请稍后再试！");
    }
}
