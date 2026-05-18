package com.example.shop.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 自定义Log局部过滤器工厂
 *
 * 功能：在请求前后打印日志，包括请求路径、方法、耗时等信息
 *       仅对配置了该过滤器的路由生效（局部过滤器）
 *
 * 使用方式：在路由filters中添加 - Log=true
 *           true表示打印详细日志（含请求头），false仅打印基本信息
 *
 * 命名规则：类名必须以GatewayFilterFactory结尾，
 *           Spring Cloud Gateway会自动识别前缀"Log"作为过滤器名称
 */
@Slf4j
@Component
public class LogGatewayFilterFactory extends AbstractGatewayFilterFactory<LogGatewayFilterFactory.Config> {

    public LogGatewayFilterFactory() {
        super(Config.class);
    }

    /**
     * 定义配置参数的顺序
     * 与YAML中的 - Log=true 对应，映射到showDetail字段
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("showDetail");
    }

    /**
     * 定义过滤器逻辑
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Pre阶段：请求到达下游服务前执行
            long startTime = System.currentTimeMillis();
            String requestId = exchange.getRequest().getId();
            String method = exchange.getRequest().getMethodValue();
            String path = exchange.getRequest().getURI().getPath();

            log.info("[LogFilter-Pre] 请求ID: {}, 方法: {}, 路径: {}", requestId, method, path);

            if (config.isShowDetail()) {
                // 打印请求头详情（调试模式）
                exchange.getRequest().getHeaders().forEach((name, values) ->
                    log.debug("[LogFilter-Pre] 请求头: {} = {}", name, values)
                );
            }

            // Post阶段：下游服务响应后执行（通过then实现）
            return chain.filter(exchange).then(
                // then中的逻辑在响应返回时执行
                reactor.core.publisher.Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : -1;
                    log.info("[LogFilter-Post] 请求ID: {}, 状态码: {}, 耗时: {}ms",
                            requestId, statusCode, duration);
                })
            );
        };
    }

    /**
     * 过滤器配置类
     */
    @Data
    public static class Config {
        private boolean showDetail;
    }
}
