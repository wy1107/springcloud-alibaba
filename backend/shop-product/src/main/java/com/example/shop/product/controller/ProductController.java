package com.example.shop.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.shop.common.entity.Product;
import com.example.shop.product.handler.ProductBlockHandler;
import com.example.shop.product.handler.ProductFallbackHandler;
import com.example.shop.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Value("${server.port}")
    private String port;

    /**
     * 根据商品ID查询商品信息
     * @SentinelResource注解配置：
     * - value: 资源名称，在Sentinel控制台中显示
     * - blockHandlerClass: 限流/熔断处理类（处理BlockException）
     * - blockHandler: 指定blockHandlerClass中的处理方法名
     * - fallbackClass: 业务异常降级处理类（处理非BlockException异常）
     * - fallback: 指定fallbackClass中的处理方法名
     */
    @GetMapping("/{pid}")
    @SentinelResource(
            value = "findById",
            blockHandlerClass = ProductBlockHandler.class,
            blockHandler = "findByIdBlockHandler",
            fallbackClass = ProductFallbackHandler.class,
            fallback = "findByIdFallback"
    )
    public Product findById(@PathVariable Integer pid) {
        Product product = productService.findById(pid);
        // 输出端口号，便于观察负载均衡请求分发到哪个实例
        log.info(">>> 商品服务实例 [端口:{}] 处理查询请求, 商品ID: {}", port, pid);
        return product;
    }

    /**
     * 负载均衡验证接口：返回当前实例的端口号
     */
    @GetMapping("/port")
    public Map<String, Object> getPort() {
        log.info(">>> 商品服务实例 [端口:{}] 收到端口查询请求", port);
        Map<String, Object> result = new HashMap<>();
        result.put("port", port);
        result.put("serviceName", "service-product");
        result.put("message", "请求到达端口 " + port + " 的商品服务实例");
        return result;
    }

    /**
     * 秒杀接口 —— 演示热点参数限流 + 排队等待
     * 针对商品ID参数进行限流，防止瞬时高并发压垮系统
     *
     * 使用@SentinelResource注解将方法定义为Sentinel资源：
     * - blockHandler处理Sentinel阻断（限流/熔断/系统保护等）
     * - fallback处理业务运行时异常
     *
     * 控制台配置热点规则：参数索引0（productId），单机阈值500
     */
    @GetMapping("/seckill/{productId}")
    @SentinelResource(
            value = "seckill",
            blockHandlerClass = ProductBlockHandler.class,
            blockHandler = "seckillBlockHandler",
            fallbackClass = ProductFallbackHandler.class,
            fallback = "seckillFallback"
    )
    public String seckill(@PathVariable Integer productId) {
        log.info("秒杀请求 - 商品ID: {}", productId);
        // 模拟秒杀业务处理
        return "秒杀成功（商品ID:" + productId + "）";
    }
}
