package com.example.shop.order.handler;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.RequestOriginParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 自定义请求来源解析器，用于Sentinel授权规则
 *
 * 实现RequestOriginParser接口，从请求参数中解析调用来源标识
 * 配合Sentinel控制台的授权规则（白名单/黑名单）使用
 *
 * 使用方式：
 * - 请求中添加 origin 参数，如 /order/create?pid=1&uid=1&origin=app
 * - 在Sentinel控制台配置授权规则，指定白名单或黑名单的origin值
 */
@Slf4j
@Component
public class RequestOriginParserDefinition implements RequestOriginParser {

    /**
     * 从请求参数中获取origin字段作为调用来源标识
     * @param request HTTP请求
     * @return 调用来源标识，未携带origin参数时返回空字符串
     */
    @Override
    public String parseOrigin(HttpServletRequest request) {
        String origin = request.getParameter("origin");
        if (origin == null || origin.trim().isEmpty()) {
            origin = "";
        }
        log.info("解析请求来源: origin={}, URI={}", origin, request.getRequestURI());
        return origin;
    }
}
