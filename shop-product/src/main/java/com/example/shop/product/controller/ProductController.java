package com.example.shop.product.controller;

import com.example.shop.common.entity.Product;
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

    @GetMapping("/{pid}")
    public Product findById(@PathVariable Integer pid) {
        Product product = productService.findById(pid);
        // 输出端口号，便于观察负载均衡请求分发到哪个实例
        log.info(">>> 商品服务实例 [端口:{}] 处理查询请求, 商品ID: {}", port, pid);
        return product;
    }

    /**
     * 负载均衡验证接口：返回当前实例的端口号
     * 通过此接口可直观看到请求被分发到哪个商品服务实例
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
}
