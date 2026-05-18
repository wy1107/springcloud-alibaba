package com.example.shop.order.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestOriginParserDefinition单元测试
 * 验证授权规则的请求来源解析逻辑
 */
class RequestOriginParserDefinitionTest {

    private RequestOriginParserDefinition parser;

    @BeforeEach
    void setUp() {
        parser = new RequestOriginParserDefinition();
    }

    @Test
    @DisplayName("解析origin参数-正常值")
    void testParseOriginWithValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("origin", "app");
        String result = parser.parseOrigin(request);
        assertEquals("app", result);
    }

    @Test
    @DisplayName("解析origin参数-无origin返回空字符串")
    void testParseOriginWithoutValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String result = parser.parseOrigin(request);
        assertEquals("", result);
    }

    @Test
    @DisplayName("解析origin参数-空格返回空字符串")
    void testParseOriginWithBlankValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("origin", "   ");
        String result = parser.parseOrigin(request);
        assertEquals("", result);
    }
}
