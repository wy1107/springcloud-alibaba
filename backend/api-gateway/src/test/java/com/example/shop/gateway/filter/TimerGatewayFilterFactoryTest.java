package com.example.shop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimerGatewayFilterFactory单元测试
 *
 * 验证自定义Timer过滤器工厂的配置解析：
 * - shortcutFieldOrder配置正确
 * - Config的thresholdMs字段可正确设置
 */
public class TimerGatewayFilterFactoryTest {

    private TimerGatewayFilterFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new TimerGatewayFilterFactory();
    }

    /**
     * 测试：shortcutFieldOrder应包含thresholdMs字段
     */
    @Test
    public void testShortcutFieldOrder() {
        List<String> fields = factory.shortcutFieldOrder();
        assertEquals(1, fields.size(), "应只有一个字段");
        assertEquals("thresholdMs", fields.get(0), "字段名应为thresholdMs");
    }

    /**
     * 测试：Config的thresholdMs设置
     */
    @Test
    public void testConfigThresholdMs() {
        TimerGatewayFilterFactory.Config config = new TimerGatewayFilterFactory.Config();
        config.setThresholdMs(3000);
        assertEquals(3000, config.getThresholdMs(), "thresholdMs应为3000");
    }

    /**
     * 测试：Config的thresholdMs默认值为0
     */
    @Test
    public void testConfigDefaultThresholdMs() {
        TimerGatewayFilterFactory.Config config = new TimerGatewayFilterFactory.Config();
        assertEquals(0, config.getThresholdMs(), "默认thresholdMs应为0");
    }
}
