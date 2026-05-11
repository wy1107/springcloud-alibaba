package com.example.shop.order.service.impl;

import com.example.shop.common.entity.Order;
import com.example.shop.common.entity.Product;
import com.example.shop.order.dao.OrderDao;
import com.example.shop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Order createOrder(Integer pid, Integer uid) {
        // 1. 通过RestTemplate调用商品微服务获取商品信息
        Product product = restTemplate.getForObject(
                "http://localhost:8081/product/" + pid, Product.class);

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
