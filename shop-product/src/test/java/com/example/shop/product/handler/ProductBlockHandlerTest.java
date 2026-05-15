package com.example.shop.product.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.example.shop.common.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductBlockHandler单元测试
 * 验证Sentinel限流/熔断处理逻辑的正确性
 */
class ProductBlockHandlerTest {

    @Test
    @DisplayName("findById限流处理-FlowException")
    void testFindByIdBlockHandlerWithFlowException() {
        FlowException ex = new FlowException("test-flow");
        Product result = ProductBlockHandler.findByIdBlockHandler(1, ex);
        assertEquals(1, result.getPid());
        assertTrue(result.getPname().contains("限流/熔断降级"));
        assertEquals(0.0, result.getPprice());
        assertEquals(0, result.getStock());
    }

    @Test
    @DisplayName("findById限流处理-DegradeException")
    void testFindByIdBlockHandlerWithDegradeException() {
        DegradeException ex = new DegradeException("test-degrade");
        Product result = ProductBlockHandler.findByIdBlockHandler(2, ex);
        assertEquals(2, result.getPid());
        assertTrue(result.getPname().contains("限流/熔断降级"));
    }

    @Test
    @DisplayName("findById限流处理-任意BlockException")
    void testFindByIdBlockHandlerWithAnyBlockException() {
        // 使用FlowException作为BlockException的子类进行测试
        BlockException ex = new FlowException("any-block");
        Product result = ProductBlockHandler.findByIdBlockHandler(100, ex);
        assertEquals(100, result.getPid());
    }

    @Test
    @DisplayName("秒杀限流处理-返回429JSON")
    void testSeckillBlockHandler() {
        FlowException ex = new FlowException("seckill-flow");
        String result = ProductBlockHandler.seckillBlockHandler(1001, ex);
        assertTrue(result.contains("429"));
        assertTrue(result.contains("抢购火爆"));
    }
}
