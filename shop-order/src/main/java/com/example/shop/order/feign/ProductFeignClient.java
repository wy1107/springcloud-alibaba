package com.example.shop.order.feign;

import com.example.shop.common.entity.Product;
import com.example.shop.order.fallback.ProductFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 商品服务Feign客户端
 * 声明式调用商品微服务，替代硬编码的RestTemplate方式
 * value: 目标服务在Nacos中注册的服务名
 * 内置集成了Spring Cloud LoadBalancer，默认实现负载均衡
 *
 * Feign整合Sentinel选择fallbackFactory而非fallback：
 * - fallbackFactory可以获取到具体的异常信息（Throwable），便于排查问题
 * - fallback只能返回兜底数据，无法获取异常详情
 * - 注意：fallback和fallbackFactory只能使用其中一种
 */
@FeignClient(value = "service-product", fallbackFactory = ProductFeignClientFallbackFactory.class)
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
