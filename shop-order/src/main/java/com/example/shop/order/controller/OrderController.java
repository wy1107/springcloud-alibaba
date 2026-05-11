package com.example.shop.order.controller;

import com.example.shop.common.entity.Order;
import com.example.shop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/create")
    public Order createOrder(@RequestParam Integer pid, @RequestParam Integer uid) {
        log.info("收到下单请求, 商品ID: {}, 用户ID: {}", pid, uid);
        return orderService.createOrder(pid, uid);
    }
}
