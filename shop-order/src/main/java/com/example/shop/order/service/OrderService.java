package com.example.shop.order.service;

import com.example.shop.common.entity.Order;

public interface OrderService {

    Order createOrder(Integer pid, Integer uid);
}
