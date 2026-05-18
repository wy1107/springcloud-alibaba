package com.example.shop.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationGlobalFilter单元测试
 *
 * 验证JWT认证过滤器的逻辑：
 * - 未携带JWT的请求应放行
 * - 携带有效JWT的请求应解析并放行
 * - 携带过期JWT的请求应返回401
 * - 携带无效JWT的请求应返回401
 */
public class JwtAuthenticationGlobalFilterTest {

    private JwtAuthenticationGlobalFilter jwtFilter;
    private GatewayFilterChain mockChain;

    private static final String SECRET_KEY = "shop-gateway-secret-key-256bit";

    @BeforeEach
    public void setUp() {
        jwtFilter = new JwtAuthenticationGlobalFilter();
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * 测试：未携带JWT的请求应放行
     */
    @Test
    public void testRequestWithoutJwt() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/product/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        jwtFilter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(any());
    }

    /**
     * 测试：携带有效JWT的请求应解析用户信息并放行
     */
    @Test
    public void testRequestWithValidJwt() {
        String jwt = Jwts.builder()
                .setSubject("user123")
                .claim("authorities", "ROLE_USER,ROLE_ADMIN")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes(StandardCharsets.UTF_8))
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/product/1")
                .header("Authorization", "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        jwtFilter.filter(exchange, mockChain).block();

        verify(mockChain, times(1)).filter(any());
    }

    /**
     * 测试：携带过期JWT的请求应返回401
     */
    @Test
    public void testRequestWithExpiredJwt() {
        String jwt = Jwts.builder()
                .setSubject("user123")
                .claim("authorities", "ROLE_USER")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes(StandardCharsets.UTF_8))
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/product/1")
                .header("Authorization", "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        jwtFilter.filter(exchange, mockChain).block();

        verify(mockChain, never()).filter(any());
        assertNotNull(exchange.getResponse().getStatusCode());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    /**
     * 测试：携带无效JWT的请求应返回401
     */
    @Test
    public void testRequestWithInvalidJwt() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/product/1")
                .header("Authorization", "Bearer invalid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        jwtFilter.filter(exchange, mockChain).block();

        verify(mockChain, never()).filter(any());
        assertNotNull(exchange.getResponse().getStatusCode());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    /**
     * 测试：过滤器优先级应为-100
     */
    @Test
    public void testGetOrder() {
        assertEquals(-100, jwtFilter.getOrder(), "JwtFilter优先级应为-100");
    }
}
