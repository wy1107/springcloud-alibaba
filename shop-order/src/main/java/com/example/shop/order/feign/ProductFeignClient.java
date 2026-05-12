package com.example.shop.order.feign;

import com.example.shop.common.entity.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 商品服务Feign客户端
 * 声明式调用商品微服务，替代硬编码的RestTemplate方式
 * value: 目标服务在Nacos中注册的服务名
 * 内置集成了Spring Cloud LoadBalancer，默认实现负载均衡
 */
@FeignClient(value = "service-product")
public interface ProductFeignClient {

    /**
     * 根据商品ID查询商品信息
     * 方法签名需与商品微服务的ProductController.findById保持一致
     */
    @GetMapping("/product/{pid}")
    Product findByPid(@PathVariable("pid") Integer pid);

    /**
     * 查询商品服务实例端口（负载均衡验证用）
     */
    @GetMapping("/product/port")
    Map<String, Object> getPort();
}
