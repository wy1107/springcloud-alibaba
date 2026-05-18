package com.example.shop.gateway.predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgeRoutePredicateFactory单元测试
 *
 * 验证自定义Age断言工厂的逻辑是否正确：
 * - age在[min, max]范围内匹配
 * - age超出范围不匹配
 * - 未携带age参数不匹配
 * - age格式错误不匹配
 */
public class AgeRoutePredicateFactoryTest {

    private AgeRoutePredicateFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new AgeRoutePredicateFactory();
    }

    /**
     * 测试：age在范围内应匹配
     */
    @Test
    public void testAgeInRange() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=25").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertTrue(predicate.test(exchange), "age=25应在[18,60]范围内匹配");
    }

    /**
     * 测试：age等于最小值应匹配（边界值）
     */
    @Test
    public void testAgeAtMinBoundary() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=18").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertTrue(predicate.test(exchange), "age=18（等于min）应匹配");
    }

    /**
     * 测试：age等于最大值应匹配（边界值）
     */
    @Test
    public void testAgeAtMaxBoundary() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=60").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertTrue(predicate.test(exchange), "age=60（等于max）应匹配");
    }

    /**
     * 测试：age低于最小值不应匹配
     */
    @Test
    public void testAgeBelowMin() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=17").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertFalse(predicate.test(exchange), "age=17低于min=18不应匹配");
    }

    /**
     * 测试：age超过最大值不应匹配
     */
    @Test
    public void testAgeAboveMax() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=61").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertFalse(predicate.test(exchange), "age=61超过max=60不应匹配");
    }

    /**
     * 测试：未携带age参数不应匹配
     */
    @Test
    public void testNoAgeParameter() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertFalse(predicate.test(exchange), "未携带age参数不应匹配");
    }

    /**
     * 测试：age参数格式错误不应匹配
     */
    @Test
    public void testInvalidAgeFormat() {
        AgeRoutePredicateFactory.Config config = new AgeRoutePredicateFactory.Config();
        config.setMin(18);
        config.setMax(60);

        Predicate<ServerWebExchange> predicate = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test?age=abc").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertFalse(predicate.test(exchange), "age=abc格式错误不应匹配");
    }

    /**
     * 测试：shortcutFieldOrder配置正确
     */
    @Test
    public void testShortcutFieldOrder() {
        java.util.List<String> fields = factory.shortcutFieldOrder();
        assertEquals(2, fields.size(), "应有2个字段");
        assertEquals("min", fields.get(0), "第一个字段应为min");
        assertEquals("max", fields.get(1), "第二个字段应为max");
    }
}
