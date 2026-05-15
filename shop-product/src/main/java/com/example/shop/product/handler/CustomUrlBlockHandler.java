package com.example.shop.product.handler;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义Sentinel URL级别的限流/熔断异常返回处理器
 * 实现BlockExceptionHandler接口，替代默认的"Blocked by Sentinel"提示
 *
 * 处理BlockException的五种子异常：
 * - FlowException: 流控规则触发
 * - DegradeException: 降级规则触发
 * - ParamFlowException: 热点参数限流触发
 * - AuthorityException: 授权规则触发
 * - SystemBlockException: 系统保护规则触发
 */
@Slf4j
@Component
public class CustomUrlBlockHandler implements BlockExceptionHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException ex) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=utf-8");

        Map<String, Object> result = new HashMap<>();
        result.put("code", 429);

        // 根据异常类型返回不同的提示信息，便于快速定位问题
        if (ex instanceof FlowException) {
            result.put("msg", "接口被限流了-FlowException");
            log.warn("Sentinel流控触发: URI={}, 规则={}", request.getRequestURI(), ex.getRule());
        } else if (ex instanceof DegradeException) {
            result.put("msg", "接口被降级了-DegradeException");
            log.warn("Sentinel降级触发: URI={}, 规则={}", request.getRequestURI(), ex.getRule());
        } else if (ex instanceof ParamFlowException) {
            result.put("msg", "热点参数限流-ParamFlowException");
            log.warn("Sentinel热点参数限流触发: URI={}", request.getRequestURI());
        } else if (ex instanceof AuthorityException) {
            result.put("msg", "授权规则不通过-AuthorityException");
            log.warn("Sentinel授权规则触发: URI={}", request.getRequestURI());
        } else if (ex instanceof SystemBlockException) {
            result.put("msg", "系统保护规则触发-SystemBlockException");
            log.warn("Sentinel系统保护触发: URI={}, 规则={}", request.getRequestURI(), ex.getRule());
        } else {
            result.put("msg", "Sentinel保护触发-UnknownBlockException");
            log.warn("Sentinel未知保护触发: URI={}", request.getRequestURI());
        }

        response.getWriter().write(JSON.toJSONString(result));
    }
}
