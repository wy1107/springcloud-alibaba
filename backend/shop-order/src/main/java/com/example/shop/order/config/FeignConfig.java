package com.example.shop.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Feign拦截器配置
 *
 * 功能：将当前请求中的用户信息Header转发给下游微服务
 *       解决网关解析JWT后注入的X-User-Id和X-User-Authorities无法自动传递的问题
 *
 * 场景：网关 → 订单服务（携带X-User-Id/X-User-Authorities）
 *            → 订单服务通过Feign调用商品服务（需手动传递用户信息Header）
 *
 * 原理：Feign发起远程调用前，RequestInterceptor可以修改RequestTemplate，
 *       从当前Servlet请求中读取Header并写入Feign请求模板
 */
@Slf4j
@Configuration
public class FeignConfig {

    /**
     * 用户信息转发拦截器
     * 将X-User-Id和X-User-Authorities从当前请求传递到Feign远程调用中
     */
    @Bean
    public RequestInterceptor userInfoForwardInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 从RequestContextHolder获取当前Servlet请求（仅在线程绑定的Servlet环境中有效）
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    // 传递用户ID
                    String userId = request.getHeader("X-User-Id");
                    if (userId != null) {
                        template.header("X-User-Id", userId);
                        log.debug("Feign拦截器: 传递X-User-Id={}", userId);
                    }
                    // 传递用户权限
                    String authorities = request.getHeader("X-User-Authorities");
                    if (authorities != null) {
                        template.header("X-User-Authorities", authorities);
                        log.debug("Feign拦截器: 传递X-User-Authorities={}", authorities);
                    }
                }
            }
        };
    }
}
