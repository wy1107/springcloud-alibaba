package com.example.shop.order.fallback;

import com.example.shop.common.entity.Product;
import com.example.shop.order.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 商品Feign客户端的FallbackFactory（推荐方式，优于fallback）
 *
 * 优势：可以获取到具体的异常信息，便于排查问题原因
 * fallback方式只能返回兜底数据，无法获取异常详情
 *
 * 注意：fallback和fallbackFactory只能使用其中一种方式
 */
@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        // 使用匿名内部类实现FeignClient接口，所有方法都提供降级逻辑
        return new ProductFeignClient() {

            /**
             * 商品查询降级：返回兜底商品信息
             * cause参数携带具体异常信息（如连接超时、服务不可用等）
             */
            @Override
            public Product findByPid(Integer pid) {
                log.error("商品服务调用降级, 商品ID: {}, 异常原因: {}", pid, cause.getMessage());
                Product product = new Product();
                product.setPid(pid);
                product.setPname("商品服务降级-触发FallbackFactory保护");
                product.setPprice(0.0);
                product.setStock(0);
                return product;
            }

            /**
             * 端口查询降级：返回降级提示
             */
            @Override
            public Map<String, Object> getPort() {
                log.error("商品服务端口查询降级, 异常原因: {}", cause.getMessage());
                Map<String, Object> result = new HashMap<>();
                result.put("port", "unknown");
                result.put("serviceName", "service-product");
                result.put("message", "商品服务不可用，触发FallbackFactory降级，原因: " + cause.getMessage());
                return result;
            }
        };
    }
}
