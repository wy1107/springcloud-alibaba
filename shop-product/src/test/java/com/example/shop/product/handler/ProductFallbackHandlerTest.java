package com.example.shop.product.handler;

import com.example.shop.common.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductFallbackHandler单元测试
 * 验证Sentinel业务异常降级处理逻辑的正确性
 */
class ProductFallbackHandlerTest {

    @Test
    @DisplayName("findById降级处理-包含异常信息")
    void testFindByIdFallback() {
        RuntimeException ex = new RuntimeException("数据库连接超时");
        Product result = ProductFallbackHandler.findByIdFallback(1, ex);
        assertEquals(1, result.getPid());
        assertTrue(result.getPname().contains("异常降级"));
        assertTrue(result.getPname().contains("数据库连接超时"));
    }

    @Test
    @DisplayName("秒杀降级处理-返回500JSON")
    void testSeckillFallback() {
        NullPointerException ex = new NullPointerException("商品不存在");
        String result = ProductFallbackHandler.seckillFallback(1001, ex);
        assertTrue(result.contains("500"));
        assertTrue(result.contains("秒杀服务异常"));
        assertTrue(result.contains("商品不存在"));
    }
}
