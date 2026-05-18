package com.example.shop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogGatewayFilterFactory单元测试
 *
 * 验证自定义Log过滤器工厂的配置解析：
 * - shortcutFieldOrder配置正确
 * - Config的showDetail字段可正确设置
 */
public class LogGatewayFilterFactoryTest {

    private LogGatewayFilterFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new LogGatewayFilterFactory();
    }

    /**
     * 测试：shortcutFieldOrder应包含showDetail字段
     */
    @Test
    public void testShortcutFieldOrder() {
        List<String> fields = factory.shortcutFieldOrder();
        assertEquals(1, fields.size(), "应只有一个字段");
        assertEquals("showDetail", fields.get(0), "字段名应为showDetail");
    }

    /**
     * 测试：Config的showDetail设置为true
     */
    @Test
    public void testConfigShowDetailTrue() {
        LogGatewayFilterFactory.Config config = new LogGatewayFilterFactory.Config();
        config.setShowDetail(true);
        assertTrue(config.isShowDetail(), "showDetail应为true");
    }

    /**
     * 测试：Config的showDetail设置为false
     */
    @Test
    public void testConfigShowDetailFalse() {
        LogGatewayFilterFactory.Config config = new LogGatewayFilterFactory.Config();
        config.setShowDetail(false);
        assertFalse(config.isShowDetail(), "showDetail应为false");
    }
}
