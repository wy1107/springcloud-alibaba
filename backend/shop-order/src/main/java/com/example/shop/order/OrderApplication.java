package com.example.shop.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 订单微服务启动类
 * @EnableDiscoveryClient 开启Nacos服务注册与发现
 * @EnableFeignClients 开启Feign声明式服务调用
 */
@SpringBootApplication
@EntityScan("com.example.shop.common.entity")
@EnableDiscoveryClient
@EnableFeignClients
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    /**
     * 注册RestTemplate，添加@LoadBalanced注解实现客户端负载均衡
     * Spring Cloud 2021.x已移除Ribbon，使用Spring Cloud LoadBalancer替代
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
