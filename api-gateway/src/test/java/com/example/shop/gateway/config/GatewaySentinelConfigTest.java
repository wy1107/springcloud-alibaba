package com.example.shop.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GatewaySentinelConfig单元测试
 *
 * 验证Sentinel网关限流配置的初始化：
 * - API分组正确定义
 * - 流控规则正确加载
 * - 规则数量与预期一致
 */
public class GatewaySentinelConfigTest {

    private GatewaySentinelConfig config;

    @BeforeEach
    public void setUp() {
        config = new GatewaySentinelConfig();
        config.initGatewayRules();
    }

    /**
     * 测试：API分组应包含product_api, order_api, user_api
     */
    @Test
    public void testApiDefinitionsLoaded() {
        Set<ApiDefinition> apiDefs = GatewayApiDefinitionManager.getApiDefinitions();
        Set<String> apiNames = apiDefs.stream()
                .map(ApiDefinition::getApiName)
                .collect(Collectors.toSet());
        assertTrue(apiNames.contains("product_api"), "应包含product_api");
        assertTrue(apiNames.contains("order_api"), "应包含order_api");
        assertTrue(apiNames.contains("user_api"), "应包含user_api");
    }

    /**
     * 测试：流控规则应包含4条（3个API分组 + 1个route维度）
     */
    @Test
    public void testFlowRulesLoaded() {
        Set<?> rules = GatewayRuleManager.getRules();
        assertEquals(4, rules.size(), "应包含4条流控规则");
    }

    /**
     * 测试：API分组数量应为3
     */
    @Test
    public void testApiDefinitionCount() {
        Set<ApiDefinition> apiDefs = GatewayApiDefinitionManager.getApiDefinitions();
        assertEquals(3, apiDefs.size(), "API分组数量应为3");
    }
}
