# CODEBUDDY.md This file provides guidance to DHCCJRYYCPB when working with code in this repository.

## Build Commands

**Install common module first (required before any other build):**
```
mvn clean install -DskipTests -f shop-common/pom.xml
```

**Build all modules:**
```
mvn clean package -DskipTests
```

**Build a single service:**
```
mvn clean package -DskipTests -f shop-order/pom.xml
```

**Run a single service:**
```
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar
java -jar shop-order/target/shop-order-1.0-SNAPSHOT.jar
java -jar shop-user/target/shop-user-1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
```

**Run with custom port (for multi-instance load balancing):**
```
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar --server.port=8082
```

**Run tests for a single module:**
```
mvn test -f shop-product/pom.xml
mvn test -f shop-order/pom.xml
mvn test -f api-gateway/pom.xml
```

**Run a single test class:**
```
mvn test -f shop-product/pom.xml -Dtest=ProductBlockHandlerTest
```

## Architecture

This is a Spring Cloud Alibaba microservice e-commerce project (shop system) built on **Spring Boot 2.6.13 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0 + JDK 1.8**. All services share a single MySQL database named `shop`.

### Module Structure

- **shop-common** — Shared entities (`Product`, `Order`, `User`) with JPA annotations + Lombok. No runnable main class. Must be `mvn install`ed before building other modules. Uses `@EntityScan("com.example.shop.common.entity")` in each service's main class to scan entities from this JAR. Note: Lombok is `optional` in shop-common and won't transit to dependents — each service must declare its own Lombok dependency.

- **shop-product** (port 8081, service name `service-product`) — Product service provider. Uses `@SentinelResource` on controller methods with separate `blockHandlerClass` (for BlockException) and `fallbackClass` (for business exceptions). Both are static methods in external handler classes.

- **shop-user** (port 8071, service name `service-user`) — User service provider. Simplest service with no Feign or LoadBalancer dependencies.

- **shop-order** (port 8091, service name `service-order`) — Order service consumer. Calls product service via Feign (`ProductFeignClient`). Uses `FallbackFactory` (not `fallback`) to get exception details (`Throwable`). Only this service configures Sentinel rule persistence to Nacos (flow + degrade datasources). Also has `RequestOriginParserDefinition` for Sentinel authorization rules. Feign interceptor (`FeignConfig`) forwards `X-User-Id` and `X-User-Authorities` headers from Gateway to downstream services.

- **api-gateway** (port 9000, service name `api-gateway`) — API Gateway based on Spring Cloud Gateway (WebFlux/Netty). **Must NOT include spring-boot-starter-web**. Routes requests to microservices via `lb://` URIs with Nacos service discovery. Custom `AgeRoutePredicateFactory` for age-range routing, `LogGatewayFilterFactory` and `TimerGatewayFilterFactory` as local filters. Two `GlobalFilter`s: `JwtAuthenticationGlobalFilter` (order=-100, parses JWT and injects X-User-Id/X-User-Authorities headers) and `AuthGlobalFilter` (order=0, token-based auth with /user/** whitelist). Sentinel gateway rate limiting with API groups (product_api:100, order_api:200, user_api:50) and route dimension (product_route:150). Sentinel communication port: 8722 (staggered from product:8719, order:8720, user:8721).

### Inter-Service Communication

Order → Product calls go through: `ProductFeignClient` (Feign dynamic proxy) → Spring Cloud LoadBalancer (round-robin by default) → Nacos service discovery → HTTP to product instance. Both RestTemplate (`@LoadBalanced`) and Feign are registered in `OrderApplication`, but current business logic uses Feign exclusively.

### Sentinel Integration

All three services integrate Sentinel with **staggered communication ports** to avoid conflicts on the same host:
- product: 8719, order: 8720, user: 8721, gateway: 8722
- Dashboard address: `localhost:8858` (not the default 8080)
- `web-context-unify: false` is set on all services to support link-level flow control

**Two levels of Sentinel exception handling exist:**
1. **URL-level** (`CustomUrlBlockHandler` implementing `BlockExceptionHandler`) — handles all HTTP endpoints without `@SentinelResource`, returns JSON `{"code":429,"msg":"..."}` with specific BlockException type
2. **Resource-level** (`@SentinelResource` blockHandler/fallback) — for annotated methods like `findById` and `seckill`, returns domain objects (e.g., degraded `Product` with zero price)

**Feign + Sentinel** uses `fallbackFactory` (not `fallback`) so the fallback can log the actual `Throwable` cause. The `@FeignClient` annotation specifies `fallbackFactory = ProductFeignClientFallbackFactory.class`.

### Key Constraints

- **Java 8 only** — no `var` keyword, explicit type declarations required in all code including tests
- **BlockHandler methods in external classes must be `static`**, and their signatures must match the original method parameters plus a trailing `BlockException`
- **Fallback methods in external classes must be `static`**, signatures match original plus optional `Throwable`
- **Feign interface method signatures must exactly match** the target Controller (path, HTTP method, parameter annotations)
- **`order` is a MySQL reserved word** — the `Order` entity uses `@Table(name = "shop_order")` to avoid SQL errors
- **Parent POM's `spring-boot-maven-plugin` has `<skip>true</skip>`** — each runnable submodule overrides with `<skip>false</skip>`. Do not remove this, or shop-common will fail to build (no main class)
- **MySQL 5.x driver** uses `com.mysql.jdbc.Driver`; MySQL 8.x would require `com.mysql.cj.jdbc.Driver`
- **`SystemBlockException` import path**: `com.alibaba.csp.sentinel.slots.system` (not `slots.block.system`)
- **Gateway MUST NOT include spring-boot-starter-web** — it uses WebFlux/Netty; including Servlet container will cause startup failure
- **Gateway predicate/filter factory naming**: class names must end with `RoutePredicateFactory` or `GatewayFilterFactory` respectively
- **BlockRequestHandler for Gateway Sentinel** returns `Mono<ServerResponse>` (not `Mono<Void>`)
- **GatewayApiDefinitionManager.getApiDefinitions()** returns `Set<ApiDefinition>`, not a Map

### Infrastructure Dependencies

Start in this order: MySQL (port 3306, database `shop`) → Nacos Server (port 8848) → Sentinel Dashboard (port 8858) → Product service → User service → Order service → Gateway service. JPA `ddl-auto=update` auto-creates tables; only test data INSERTs are needed.
