package com.example.shop.user.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一异常返回格式：{"code": xxx, "msg": "xxx"}
 * 与Gateway的401/429响应格式保持一致
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        log.warn("[业务异常] {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("msg", e.getMessage());
        return result;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("[系统异常]", e);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("msg", "服务器内部错误");
        return result;
    }
}
