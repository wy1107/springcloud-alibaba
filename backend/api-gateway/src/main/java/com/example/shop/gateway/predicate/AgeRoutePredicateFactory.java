package com.example.shop.gateway.predicate;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 自定义Age路由断言工厂
 *
 * 功能：根据请求参数中的age字段值进行路由判断，
 *       只有age在[min, max]范围内的请求才匹配该路由
 *
 * 使用方式：在路由配置中添加 - Age=18,60
 *
 * 命名规则：类名必须以RoutePredicateFactory结尾，
 *           Spring Cloud Gateway会自动识别前缀"Age"作为断言名称
 *
 * 继承AbstractRoutePredicateFactory，需实现：
 * 1. shortcutFieldOrder()：定义配置参数的顺序和字段映射
 * 2. apply()：定义断言逻辑
 */
@Slf4j
@Component
public class AgeRoutePredicateFactory extends AbstractRoutePredicateFactory<AgeRoutePredicateFactory.Config> {

    public AgeRoutePredicateFactory() {
        super(Config.class);
    }

    /**
     * 定义配置参数的顺序
     * 与YAML中的 - Age=18,60 对应，第一个值映射到min，第二个值映射到max
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("min", "max");
    }

    /**
     * 定义断言逻辑：检查请求参数中的age是否在[min, max]范围内
     */
    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            // 从请求参数中获取age
            String ageStr = exchange.getRequest().getQueryParams().getFirst("age");
            if (ageStr == null || ageStr.isEmpty()) {
                log.debug("Age断言: 请求未携带age参数，不匹配");
                return false;
            }
            try {
                int age = Integer.parseInt(ageStr);
                boolean match = age >= config.getMin() && age <= config.getMax();
                log.debug("Age断言: age={}, 范围[{},{}], 匹配结果={}", age, config.getMin(), config.getMax(), match);
                return match;
            } catch (NumberFormatException e) {
                log.debug("Age断言: age参数格式错误 [{}]", ageStr);
                return false;
            }
        };
    }

    /**
     * 断言配置类
     * 存放YAML中配置的min和max值
     */
    @Data
    public static class Config {
        private int min;
        private int max;
    }
}
