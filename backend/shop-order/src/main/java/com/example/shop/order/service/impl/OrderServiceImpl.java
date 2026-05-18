package com.example.shop.order.service.impl;

import com.example.shop.common.entity.Order;
import com.example.shop.common.entity.Product;
import com.example.shop.order.dao.OrderDao;
import com.example.shop.order.feign.ProductFeignClient;
import com.example.shop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 订单服务实现类
 * 使用Feign声明式客户端调用商品微服务，替代硬编码的RestTemplate方式
 * Feign内置集成Spring Cloud LoadBalancer，自动实现客户端负载均衡
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Override
    public Order createOrder(Integer pid, Integer uid) {
        // 1. 通过Feign客户端调用商品微服务获取商品信息（自动负载均衡）
        Product product = productFeignClient.findByPid(pid);

        log.info("查询到商品信息: {}", product);

        // 2. 创建订单
        Order order = new Order();
        order.setUid(uid);
        order.setUsername("测试用户");
        order.setPid(pid);
        order.setPname(product.getPname());
        order.setPprice(product.getPprice());
        order.setNumber(1);

        // 3. 保存订单
        orderDao.save(order);

        log.info("订单创建成功: {}", order);
        return order;
    }
}
