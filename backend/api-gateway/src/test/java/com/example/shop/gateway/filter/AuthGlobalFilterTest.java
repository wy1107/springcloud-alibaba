package com.example.shop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthGlobalFilter单元测试
 *
 * 验证全局鉴权过滤器的逻辑：
 * - 携带token的请求应放行
 * - 未携带token的非白名单请求应拒绝（返回401）
 * - 白名单路径即使未携带token也应放行
 */
public class AuthGlobalFilterTest {

    private AuthGlobalFilter authGlobalFilter;
    private GatewayFilterChain mockChain;

    @BeforeEach
    public void setUp() {
        authGlobalFilter = new AuthGlobalFilter();
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * 测试：携带token的请求应放行
     */
    @Test
    public void testRequestWithToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/product/1?token=abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = authGlobalFilter.filter(exchange, mockChain);

        result.block();
        verify(mockChain, times(1)).filter(exchange);
    }

    /**
     * 测试：未携带token的非白名单请求应返回401
     */
    @Test
    public void testRequestWithoutTokenNonWhitelisted() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/product/1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        authGlobalFilter.filter(exchange, mockChain).block();

        // 不应放行到下游
        verify(mockChain, never()).filter(any());
        // 响应状态码应为401
        assertNotNull(exchange.getResponse().getStatusCode());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    /**
     * 测试：白名单路径（/user/**）未携带token也应放行
     */
    @Test
    public void testWhitelistedPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = authGlobalFilter.filter(exchange, mockChain);

        result.block();
        verify(mockChain, times(1)).filter(exchange);
    }

    /**
     * 测试：过滤器优先级应为0
     */
    @Test
    public void testGetOrder() {
        assertEquals(0, authGlobalFilter.getOrder(), "AuthGlobalFilter优先级应为0");
    }
}
