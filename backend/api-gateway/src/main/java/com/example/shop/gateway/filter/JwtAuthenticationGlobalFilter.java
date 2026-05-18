package com.example.shop.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT认证全局过滤器
 *
 * 功能：解析并验证JWT令牌，将用户信息通过Header传递给下游微服务
 *
 * 处理流程：
 * 1. 从请求Header中获取Authorization字段
 * 2. 若未携带JWT则放行（由具体服务决定是否需要认证，灵活设计）
 * 3. 若携带JWT则解析验证，提取userId和authorities
 * 4. 将用户信息写入X-User-Id和X-User-Authorities请求头，传递给下游
 * 5. JWT过期或无效则返回401响应
 *
 * 优先级：order=-100，比AuthGlobalFilter(order=0)优先执行
 *         先完成JWT解析和用户信息注入，再做权限校验
 */
@Slf4j
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    /**
     * JWT签名密钥（应与认证服务保持一致）
     * 生产环境中应通过配置中心或环境变量注入，严禁硬编码
     */
    private static final String SECRET_KEY = "shop-gateway-secret-key-256bit";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 未携带JWT时放行（由具体服务决定是否需要认证）
        if (StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("[JwtFilter] 请求未携带JWT, 路径: {}", request.getURI().getPath());
            return chain.filter(exchange);
        }

        String jwt = authHeader.substring(7); // 截取"Bearer "之后的部分
        try {
            // 解析JWT令牌
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(jwt)
                    .getBody();

            // 提取用户信息
            String userId = claims.getSubject();
            String authorities = claims.get("authorities", String.class);

            log.debug("[JwtFilter] JWT验证通过, userId: {}, 路径: {}", userId, request.getURI().getPath());

            // 将用户信息添加到请求头中，传递给下游微服务
            // 下游服务可通过 @RequestHeader("X-User-Id") 获取用户ID
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Authorities", authorities != null ? authorities : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("[JwtFilter] JWT已过期, 路径: {}", request.getURI().getPath());
            return unauthorizedResponse(exchange, "Token expired");
        } catch (JwtException e) {
            log.warn("[JwtFilter] JWT无效, 路径: {}, 原因: {}", request.getURI().getPath(), e.getMessage());
            return unauthorizedResponse(exchange, "Invalid token");
        }
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
        // 优先级高于AuthGlobalFilter(order=0)，先完成JWT解析再进行鉴权
        return -100;
    }
}
