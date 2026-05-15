package com.example.shop.order.handler;

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
 * 订单服务自定义Sentinel URL级别异常返回处理器
 * 对所有未使用@SentinelResource注解的接口提供统一的限流/熔断JSON响应
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

        // 根据异常类型返回不同的提示信息
        if (ex instanceof FlowException) {
            result.put("msg", "订单接口被限流了-FlowException");
        } else if (ex instanceof DegradeException) {
            result.put("msg", "订单接口被降级了-DegradeException");
        } else if (ex instanceof ParamFlowException) {
            result.put("msg", "订单热点参数限流-ParamFlowException");
        } else if (ex instanceof AuthorityException) {
            result.put("msg", "订单授权规则不通过-AuthorityException");
        } else if (ex instanceof SystemBlockException) {
            result.put("msg", "订单系统保护规则触发-SystemBlockException");
        } else {
            result.put("msg", "订单Sentinel保护触发-UnknownBlockException");
        }

        log.warn("Sentinel保护触发: URI={}, 异常类型={}", request.getRequestURI(), ex.getClass().getSimpleName());
        response.getWriter().write(JSON.toJSONString(result));
    }
}
