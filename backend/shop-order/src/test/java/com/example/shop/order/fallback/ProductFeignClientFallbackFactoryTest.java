package com.example.shop.order.fallback;

import com.example.shop.common.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductFeignClientFallbackFactory单元测试
 * 验证Feign整合Sentinel的FallbackFactory降级逻辑
 */
class ProductFeignClientFallbackFactoryTest {

    private ProductFeignClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new ProductFeignClientFallbackFactory();
    }

    @Test
    @DisplayName("findByPid降级-连接超时场景")
    void testFindByPidFallbackWithTimeout() {
        RuntimeException cause = new RuntimeException("连接超时: service-product");
        Product result = fallbackFactory.create(cause).findByPid(1);
        assertEquals(1, result.getPid());
        assertTrue(result.getPname().contains("降级"));
        assertEquals(0.0, result.getPprice());
    }

    @Test
    @DisplayName("findByPid降级-服务不可用场景")
    void testFindByPidFallbackWithServiceUnavailable() {
        RuntimeException cause = new RuntimeException("Service Unavailable: service-product");
        Product result = fallbackFactory.create(cause).findByPid(5);
        assertEquals(5, result.getPid());
        assertTrue(result.getPname().contains("FallbackFactory"));
    }

    @Test
    @DisplayName("getPort降级-返回unknown端口")
    void testGetPortFallback() {
        RuntimeException cause = new RuntimeException("服务不可用");
        Map<String, Object> result = fallbackFactory.create(cause).getPort();
        assertEquals("unknown", result.get("port"));
        assertEquals("service-product", result.get("serviceName"));
        assertTrue(((String) result.get("message")).contains("降级"));
    }
}
