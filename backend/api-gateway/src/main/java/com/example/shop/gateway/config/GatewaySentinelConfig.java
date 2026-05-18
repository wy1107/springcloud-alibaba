package com.example.shop.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sentinel网关限流配置
 *
 * 功能：
 * 1. 定义API分组（按业务维度聚合路由，便于统一限流管理）
 * 2. 定义网关流控规则（API分组维度 + route维度）
 * 3. 自定义限流异常响应（替代默认的429错误页面，返回JSON格式）
 *
 * 两种资源维度：
 * - route维度：资源名为routeId（如product_route），对整条路由限流
 * - API分组维度：资源名为自定义API名称（如product_api），按URL模式匹配限流
 *
 * 注意：
 * - BlockRequestHandler接口返回Mono<ServerResponse>而非Mono<Void>
 * - 规则通过代码定义（@PostConstruct），也可通过Sentinel Dashboard动态配置
 */
@Slf4j
@Configuration
public class GatewaySentinelConfig {

    /**
     * 初始化API分组和网关流控规则
     * @PostConstruct在Spring Bean初始化后自动执行
     */
    @PostConstruct
    public void initGatewayRules() {
        initApiDefinitions();
        initFlowRules();
        initBlockHandler();
    }

    /**
     * 定义API分组
     * 将URL模式匹配到逻辑分组，便于按业务维度统一限流
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> apiDefinitions = new HashSet<>();

        // 商品API分组：匹配所有/product/**路径的请求
        ApiDefinition productApi = new ApiDefinition("product_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/product/**")
                            .setMatchStrategy(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});

        // 订单API分组：匹配所有/order/**路径的请求
        ApiDefinition orderApi = new ApiDefinition("order_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/order/**")
                            .setMatchStrategy(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});

        // 用户API分组：匹配所有/user/**路径的请求
        ApiDefinition userApi = new ApiDefinition("user_api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/user/**")
                            .setMatchStrategy(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});

        apiDefinitions.add(productApi);
        apiDefinitions.add(orderApi);
        apiDefinitions.add(userApi);
        GatewayApiDefinitionManager.loadApiDefinitions(apiDefinitions);
        log.info("Sentinel网关API分组初始化完成: product_api, order_api, user_api");
    }

    /**
     * 定义网关流控规则
     * 同时使用API分组维度和route维度
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // === API分组维度限流 ===
        // 商品API分组：QPS限制100（商品查询是高频操作，允许较高QPS）
        rules.add(new GatewayFlowRule("product_api")
                .setResourceMode(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(100)
                .setIntervalSec(1));

        // 订单API分组：QPS限制200（下单操作频率较高但需要保障吞吐）
        rules.add(new GatewayFlowRule("order_api")
                .setResourceMode(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(200)
                .setIntervalSec(1));

        // 用户API分组：QPS限制50（用户查询频率相对较低）
        rules.add(new GatewayFlowRule("user_api")
                .setResourceMode(com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(50)
                .setIntervalSec(1));

        // === route维度限流 ===
        // 商品路由：QPS限制150（route维度比API分组更粗粒度）
        rules.add(new GatewayFlowRule("product_route")
                .setCount(150)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel网关流控规则初始化完成: product_api=100, order_api=200, user_api=50, product_route=150");
    }

    /**
     * 自定义限流异常处理器
     * 当请求被Sentinel限流时，返回JSON格式的错误响应
     *
     * 注意：BlockRequestHandler的handle方法返回Mono<ServerResponse>
     */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable t) {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 429);
                result.put("msg", "访问过于频繁，请稍后重试");

                log.warn("[Sentinel网关限流] 路径: {}, 限流原因: {}",
                        exchange.getRequest().getURI().getPath(), t.getClass().getSimpleName());

                return ServerResponse
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Mono.just(JSON.toJSONBytes(result)), byte[].class);
            }
        });
        log.info("Sentinel网关自定义限流异常处理器初始化完成");
    }
}
