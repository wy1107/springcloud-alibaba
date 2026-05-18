package com.example.shop.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API网关服务启动类
 *
 * 关键约束：Gateway基于WebFlux，不能引入spring-boot-starter-web
 * 否则会导致Netty启动失败（Servlet容器与Reactive容器冲突）
 *
 * @EnableDiscoveryClient 开启Nacos服务注册与发现，网关自身也注册到Nacos
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
