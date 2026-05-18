package com.example.shop.order.controller;

import com.example.shop.common.entity.Order;
import com.example.shop.order.feign.ProductFeignClient;
import com.example.shop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @GetMapping("/create")
    public Order createOrder(@RequestParam Integer pid, @RequestParam Integer uid) {
        log.info("收到下单请求, 商品ID: {}, 用户ID: {}", pid, uid);
        return orderService.createOrder(pid, uid);
    }

    /**
     * 负载均衡验证接口：通过Feign调用商品服务的端口查询接口
     * 多次调用可观察请求被分发到不同商品服务实例
     */
    @GetMapping("/lb-test")
    public Map<String, Object> loadBalanceTest() {
        Map<String, Object> result = productFeignClient.getPort();
        log.info("负载均衡测试 - 请求分发到商品服务实例: {}", result);
        return result;
    }
}
