package com.example.shop.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 自定义Timer局部过滤器工厂
 *
 * 功能：统计请求从进入网关到响应返回的总耗时，
 *       当耗时超过阈值时输出警告日志（便于性能监控和慢请求排查）
 *
 * 使用方式：在路由filters中添加 - Timer=3000
 *           3000表示耗时阈值（毫秒），超过此值输出WARN级别日志
 *
 * 命名规则：类名必须以GatewayFilterFactory结尾，
 *           Spring Cloud Gateway会自动识别前缀"Timer"作为过滤器名称
 */
@Slf4j
@Component
public class TimerGatewayFilterFactory extends AbstractGatewayFilterFactory<TimerGatewayFilterFactory.Config> {

    public TimerGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("thresholdMs");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            String path = exchange.getRequest().getURI().getPath();

            return chain.filter(exchange).then(
                reactor.core.publisher.Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String method = exchange.getRequest().getMethodValue();
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : -1;

                    // 超过阈值输出WARN日志，否则输出INFO日志
                    if (duration > config.getThresholdMs()) {
                        log.warn("[TimerFilter] 慢请求! 方法: {}, 路径: {}, 状态码: {}, 耗时: {}ms (阈值: {}ms)",
                                method, path, statusCode, duration, config.getThresholdMs());
                    } else {
                        log.info("[TimerFilter] 方法: {}, 路径: {}, 状态码: {}, 耗时: {}ms",
                                method, path, statusCode, duration);
                    }
                })
            );
        };
    }

    /**
     * 过滤器配置类
     * thresholdMs: 耗时阈值（毫秒），超过此值输出WARN日志
     */
    @Data
    public static class Config {
        private long thresholdMs;
    }
}
