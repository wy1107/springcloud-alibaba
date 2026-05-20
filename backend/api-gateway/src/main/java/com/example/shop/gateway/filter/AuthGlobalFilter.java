package com.example.shop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 全局鉴权过滤器
 *
 * 功能：校验请求中是否携带token参数，未携带则拒绝访问
 *       适用于需要简单认证保护的路由
 *
 * 设计说明：
 * - 实现GlobalFilter接口，对所有路由生效（无需在YAML中配置）
 * - 实现Ordered接口，order=0，在JWT过滤器(order=-100)之后执行
 * - token参数从URL查询参数中获取（简化演示，生产环境应从Header获取）
 *
 * 注意：此过滤器为简化演示，生产环境应结合OAuth2/JWT等标准认证方案
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从URL查询参数中获取token（演示用，生产环境应从Authorization Header获取）
        String token = exchange.getRequest().getQueryParams().getFirst("token");

        // 白名单路径：无需token验证的接口
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            log.debug("[AuthFilter] 白名单路径放行: {}", path);
            return chain.filter(exchange);
        }

        if (StringUtils.isEmpty(token)) {
            log.warn("[AuthFilter] 请求未携带token, 路径: {}", path);
            return unauthorizedResponse(exchange, "Missing token");
        }

        // token校验通过，放行
        log.debug("[AuthFilter] token校验通过, 路径: {}", path);
        return chain.filter(exchange);
    }

    /**
     * 判断路径是否在白名单中（无需token验证）
     * 实际项目中可从配置中心动态获取白名单
     */
    private boolean isWhitelisted(String path) {
        // /user/** 路径全部白名单放行（包含登录接口 /user/login）
        // 健康检查端点也无需鉴权
        return path.startsWith("/user/")
                || path.equals("/actuator/health");
    }

    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        byte[] bytes = String.format("{\"code\":401,\"msg\":\"%s\"}", msg)
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // order=0，在JwtAuthenticationGlobalFilter(order=-100)之后执行
        return 0;
    }
}
