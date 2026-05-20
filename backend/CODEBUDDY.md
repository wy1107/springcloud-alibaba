# CODEBUDDY.md This file provides guidance to DHCCJRYYCPB when working with code in this repository.

## Build & Run Commands

**Prerequisites**: JDK 1.8, Maven 3.6.3+, MySQL 5.7, Nacos Server 2.x (port 8848), Sentinel Dashboard (port 8858)

```bash
# Install common module first (required before building any other module)
mvn clean install -DskipTests -f shop-common/pom.xml

# Build all modules from root
mvn clean package -DskipTests

# Build single module
mvn clean package -DskipTests -f shop-order/pom.xml

# Run a service (start order: MySQL → Nacos → shop-product → shop-user → shop-order → api-gateway)
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar

# Run tests for a single module
mvn test -f shop-order/pom.xml

# Run a single test class
mvn test -f api-gateway/pom.xml -Dtest=AuthGlobalFilterTest

# Run a single test method
mvn test -f api-gateway/pom.xml -Dtest=AuthGlobalFilterTest#testFilterWithToken
```

## Architecture

This is a Spring Cloud Alibaba microservice e-commerce project (shop domain). **Tech stack**: Spring Boot 2.6.13 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0 + MySQL 5.7 + JPA + JDK 1.8.

### Module Structure & Port Allocation

| Module | Port | Service Name | Role |
|--------|------|-------------|------|
| shop-common | - | - | Shared entities (Order, Product, User), no main class |
| shop-product | 8081 | service-product | Product provider |
| shop-user | 8071 | service-user | User provider + JWT auth |
| shop-order | 8091 | service-order | Order consumer, calls product via Feign |
| api-gateway | 9000 | api-gateway | API gateway (WebFlux, NOT spring-boot-starter-web) |

### Inter-Service Communication

- **order → product**: OpenFeign with `@FeignClient(value="service-product", fallbackFactory=ProductFeignClientFallbackFactory.class)`. Feign integrates Spring Cloud LoadBalancer for client-side load balancing.
- **Gateway → downstream services**: Route via `lb://service-name` with Nacos service discovery.
- **User info propagation**: Gateway's `JwtAuthenticationGlobalFilter` (order=-100) parses JWT and injects `X-User-Id`/`X-User-Authorities` headers. Order service's `FeignConfig` registers a `RequestInterceptor` to forward these headers to product service.

### Key Design Decisions

**Sentinel**:
- Dashboard address: `localhost:8858` (not default 8080)
- Sentinel client ports must not conflict: product=8719, order=8720, user=8721, gateway=8722
- Feign+Sentinel uses `fallbackFactory` (not `fallback`) to capture `Throwable` for debugging
- Only order service configures Nacos datasource persistence (flow+degrade rules) as demonstration
- `SystemBlockException` import: `com.alibaba.csp.sentinel.slots.system` (NOT `slots.block.system`)
- Product service uses `@SentinelResource` with separate `blockHandlerClass` and `fallbackClass` for fine-grained control
- All business services implement `BlockExceptionHandler` for URL-level Sentinel exceptions

**Gateway**:
- Based on WebFlux — **never** add `spring-boot-starter-web` dependency
- Custom predicate: `AgeRoutePredicateFactory` extends `AbstractRoutePredicateFactory`, class name MUST end with `RoutePredicateFactory`
- Custom filter factories: `LogGatewayFilterFactory`, `TimerGatewayFilterFactory` extend `AbstractGatewayFilterFactory`, class names MUST end with `GatewayFilterFactory`
- Two GlobalFilters: `JwtAuthenticationGlobalFilter`(order=-100) parses JWT first, then `AuthGlobalFilter`(order=0) checks authorization
- Sentinel gateway rate limiting uses API group dimension (product_api=100, order_api=200, user_api=50) + route dimension (product_route=150)
- `BlockRequestHandler.handleRequest()` returns `Mono<ServerResponse>` (NOT `Mono<Void>`)
- `GatewayApiDefinitionManager.loadApiDefinitions()` takes `Set<ApiDefinition>` (NOT Map)

**JWT Auth Flow**:
- User logs in via `POST /user/login` (uid+telephone) → `UserServiceImpl` issues JWT with HS256
- JWT secret key (`shop-gateway-secret-key-256bit`) must match between `UserServiceImpl` and `JwtAuthenticationGlobalFilter`
- `/user/**` is whitelisted in `AuthGlobalFilter`, no token required

### Database

All services share MySQL database `shop` (JPA ddl-auto=update creates tables automatically). Order entity uses `@Table(name="shop_order")` to avoid MySQL reserved keyword conflict. Test data in `doc/sql/shop.sql`.

### Java 8 Constraints

- No `var` keyword — always declare explicit types
- `@Value` annotation does not inject in non-Spring-managed test classes — use reflection to set fields
- `spring-boot-starter-test` includes JUnit 5 (Jupiter), not JUnit 4
