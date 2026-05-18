# 第五章 Gateway——服务网关 实现总结

> 基于 Spring Boot 2.6.13 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0

---

## 一、第五章技术要点分析

### 1.1 核心问题

没有网关存在的问题：

| 问题 | 说明 |
|------|------|
| 客户端直接调用各微服务 | 客户端需维护多个服务地址，复杂度高 |
| 每个服务独立认证 | 认证逻辑重复，无法统一管控 |
| 跨域请求复杂 | 每个服务独立处理CORS，配置不一致 |
| 安全风险 | 内部服务直接暴露，缺少统一入口保护 |

### 1.2 解决方案：Spring Cloud Gateway

```
客户端 → API Gateway (统一入口)
           ├── 路由转发 → 商品微服务 (service-product)
           ├── 路由转发 → 订单微服务 (service-order)
           ├── 路由转发 → 用户微服务 (service-user)
           ├── 统一鉴权 (AuthGlobalFilter)
           ├── JWT认证 (JwtAuthenticationGlobalFilter)
           ├── 限流熔断 (Sentinel)
           └── 自定义断言/过滤器
```

### 1.3 Spring Cloud Gateway架构

```
Gateway Client
    ↓
HttpWebHandlerAdapter (组装网关上下文)
    ↓
DispatcherHandler (分发请求)
    ↓
RoutePredicateHandlerMapping (路由查找 + 断言判断)
    ↓
FilteringWebHandler (创建过滤器链)
    ↓
Pre Filter → 微服务 → Post Filter
    ↓
Gateway Client (响应返回)
```

### 1.4 关键约束

| 约束 | 说明 |
|------|------|
| **不能引入spring-boot-starter-web** | Gateway基于WebFlux(Netty)，与Servlet容器冲突 |
| **断言工厂类名** | 必须以`RoutePredicateFactory`结尾 |
| **过滤器工厂类名** | 必须以`GatewayFilterFactory`结尾 |
| **BlockRequestHandler返回值** | `Mono<ServerResponse>`而非`Mono<Void>` |
| **Java 8** | 不支持var关键字 |

---

## 二、实现清单

### 2.1 新增模块

| 模块 | 说明 | 端口 |
|------|------|------|
| api-gateway | API网关服务 | 9000 |

### 2.2 依赖变更

| 模块 | 新增依赖 |
|------|---------|
| api-gateway | `spring-cloud-starter-gateway`, `spring-cloud-starter-alibaba-nacos-discovery`, `spring-cloud-starter-loadbalancer`, `spring-cloud-starter-alibaba-sentinel`, `spring-cloud-alibaba-sentinel-gateway`, `jjwt`, `jaxb-api`, `fastjson`, `lombok`, `spring-boot-starter-test`, `reactor-test` |
| shop-order | (FeignConfig使用现有依赖，无需新增) |

### 2.3 新增文件清单

| 文件 | 模块 | 功能 |
|------|------|------|
| `GatewayApplication.java` | api-gateway | 网关启动类 |
| `AgeRoutePredicateFactory.java` | api-gateway | 自定义Age断言工厂（请求参数age范围判断） |
| `LogGatewayFilterFactory.java` | api-gateway | 自定义Log局部过滤器（请求日志记录） |
| `TimerGatewayFilterFactory.java` | api-gateway | 自定义Timer局部过滤器（耗时统计+慢请求告警） |
| `AuthGlobalFilter.java` | api-gateway | 全局鉴权过滤器（token校验，order=0） |
| `JwtAuthenticationGlobalFilter.java` | api-gateway | JWT认证全局过滤器（JWT解析+用户信息注入，order=-100） |
| `GatewaySentinelConfig.java` | api-gateway | Sentinel网关限流配置（API分组+流控规则+限流异常处理） |
| `FeignConfig.java` | shop-order | Feign拦截器（传递X-User-Id/X-User-Authorities给下游） |
| `AgeRoutePredicateFactoryTest.java` | api-gateway | Age断言工厂测试（8个用例） |
| `AuthGlobalFilterTest.java` | api-gateway | Auth过滤器测试（4个用例） |
| `JwtAuthenticationGlobalFilterTest.java` | api-gateway | JWT过滤器测试（5个用例） |
| `LogGatewayFilterFactoryTest.java` | api-gateway | Log过滤器测试（3个用例） |
| `TimerGatewayFilterFactoryTest.java` | api-gateway | Timer过滤器测试（3个用例） |
| `GatewaySentinelConfigTest.java` | api-gateway | Sentinel配置测试（3个用例） |

### 2.4 修改文件清单

| 文件 | 变更内容 |
|------|---------|
| `pom.xml`(父POM) | modules中新增`api-gateway` |
| `shop-order/.../OrderApplication.java` | 无需修改，FeignConfig通过@Component自动注册 |

### 2.5 配置变更

| 服务 | 配置项 |
|------|--------|
| api-gateway | `server.port=9000`, `spring.cloud.gateway.routes.*`, `spring.cloud.gateway.discovery.locator.enabled=true`, `spring.cloud.sentinel.transport.dashboard=localhost:8858`, `spring.cloud.sentinel.transport.port=8722` |

---

## 三、关键设计决策

1. **Gateway端口9000**：文档2.1.2节约定，与其他服务端口范围分离
2. **不能引入spring-boot-starter-web**：Gateway基于WebFlux(Netty)，引入Servlet容器会导致启动失败
3. **Sentinel通信端口8722**：避免与product:8719, order:8720, user:8721冲突
4. **路由配置双模式**：增强版(`lb://service-name`)+简写版(`discovery.locator.enabled=true`)
5. **Feign选择fallbackFactory**：相比fallback可获取具体异常信息(Throwable)
6. **API分组限流维度**：product_api(100QPS), order_api(200QPS), user_api(50QPS) + product_route(150QPS)
7. **JWT过滤器order=-100**：先完成JWT解析和用户信息注入，再做权限校验(order=0)
8. **Feign拦截器传递用户信息**：网关注入的X-User-Id/X-User-Authorities需手动传递给下游

---

## 四、单元测试（已通过，26个用例）

| 测试类 | 用例数 | 验证内容 |
|--------|-------|---------|
| AgeRoutePredicateFactoryTest | 8 | age范围匹配/边界/超范围/无参数/格式错误/shortcutFieldOrder |
| AuthGlobalFilterTest | 4 | 携带token放行/未携带token拒绝/白名单路径/getOrder |
| JwtAuthenticationGlobalFilterTest | 5 | 无JWT放行/有效JWT放行/过期JWT拒绝/无效JWT拒绝/getOrder |
| LogGatewayFilterFactoryTest | 3 | shortcutFieldOrder/showDetail=true/showDetail=false |
| TimerGatewayFilterFactoryTest | 3 | shortcutFieldOrder/thresholdMs设置/默认值 |
| GatewaySentinelConfigTest | 3 | API分组加载/流控规则数量/分组数量 |

---

## 五、Gateway全面手动测试指南

### 5.1 测试环境准备

#### 5.1.1 基础设施检查清单

| 组件 | 地址 | 验证方法 |
|------|------|---------|
| MySQL | localhost:3306 | `mysql -u root -p -e "SELECT 1"` |
| Nacos Server | localhost:8848 | 浏览器访问 http://localhost:8848/nacos |
| Sentinel Dashboard | localhost:8858 | 浏览器访问 http://localhost:8858 |

#### 5.1.2 微服务启动顺序

```
1. ProductApplication → 端口8081
2. UserApplication → 端口8071
3. OrderApplication → 端口8091
4. GatewayApplication → 端口9000
```

```bash
# 编译打包
mvn clean package -DskipTests

# 启动各服务
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar
java -jar shop-user/target/shop-user-1.0-SNAPSHOT.jar
java -jar shop-order/target/shop-order-1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
```

#### 5.1.3 服务注册验证

启动后在Nacos控制台确认：

| 服务名 | 实例数 | 端口 |
|--------|--------|------|
| service-product | 1+ | 8081 |
| service-user | 1 | 8071 |
| service-order | 1 | 8091 |
| api-gateway | 1 | 9000 |

---

### 5.2 核心功能测试点清单

| 编号 | 测试场景 | 测试目标 | 关键验证点 |
|------|---------|---------|-----------|
| G1 | 基础路由转发-商品服务 | 路由配置 | 通过网关访问商品服务 |
| G2 | 基础路由转发-订单服务 | 路由配置 | 通过网关访问订单服务 |
| G3 | 基础路由转发-用户服务 | 路由配置 | 通过网关访问用户服务 |
| G4 | 增强版路由(lb://) | 负载均衡 | lb://service-name格式路由 |
| G5 | 简写版路由 | 服务发现 | /微服务名/接口格式访问 |
| G6 | 自定义Age断言 | 断言工厂 | age参数范围匹配 |
| G7 | 自定义Log过滤器 | 局部过滤器 | 请求日志输出 |
| G8 | 自定义Timer过滤器 | 局部过滤器 | 耗时统计输出 |
| G9 | AuthGlobalFilter-携带token | 全局鉴权 | 携带token放行 |
| G10 | AuthGlobalFilter-未携带token | 全局鉴权 | 未携带token拒绝 |
| G11 | AuthGlobalFilter-白名单 | 全局鉴权 | /user/**路径放行 |
| G12 | JWT认证-有效token | JWT解析 | JWT解析+Header注入 |
| G13 | JWT认证-过期token | JWT解析 | 返回401 |
| G14 | JWT认证-无效token | JWT解析 | 返回401 |
| G15 | Sentinel网关限流-API分组 | 限流 | product_api超QPS限流 |
| G16 | Sentinel网关限流-route维度 | 限流 | product_route超QPS限流 |
| G17 | 自定义限流异常处理 | 异常响应 | 返回JSON格式429 |
| G18 | Feign拦截器传递用户信息 | 服务间调用 | X-User-Id传递到下游 |

---

### 5.3 路由转发测试（G1-G5）

#### G1-G3: 基础路由转发

**目的**：验证Gateway能正确将请求路由到各微服务

```powershell
# G1: 通过网关访问商品服务（需携带token，/product/**非白名单）
curl http://localhost:9000/product/1?token=test

# G2: 通过网关访问订单服务（创建订单，需携带token）
curl "http://localhost:9000/order/create?pid=1&uid=1&token=test"

# G3: 通过网关访问用户服务（白名单路径，无需token）
curl http://localhost:9000/user/1
```

**预期结果**：

| 请求 | 预期响应 |
|------|---------|
| GET /product/1?token=test | 返回商品JSON `{"pid":1,"pname":"小米手机",...}` |
| GET /order/create?pid=1&uid=1&token=test | 返回订单JSON（/order/**需token） |
| GET /user/1 | 返回用户JSON（/user/**白名单，无需token） |

> **注意**：若未携带token访问非白名单路径，AuthGlobalFilter会拦截并返回 `{"code":401,"msg":"Missing token"}`

#### G4: 增强版路由(lb://)

**目的**：验证lb://格式的路由能从Nacos获取服务地址并负载均衡

```powershell
# 启动第二个商品服务实例
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar --server.port=8082

# 通过网关多次访问商品服务，观察负载均衡
curl http://localhost:9000/product/port?token=test
curl http://localhost:9000/product/port?token=test
curl http://localhost:9000/product/port?token=test
```

**预期结果**：端口在8081/8082间交替出现（轮询策略）

#### G5: 简写版路由

**目的**：验证通过 网关地址/微服务名/接口 格式访问

```powershell
# 简写版访问（需携带token，因为非白名单）
curl http://localhost:9000/SERVICE-PRODUCT/product/1?token=test
# 或小写（已配置lower-case-service-id: true）
curl http://localhost:9000/service-product/product/1?token=test
```

**预期结果**：返回商品JSON

---

### 5.4 自定义断言/过滤器测试（G6-G8）

#### G6: 自定义Age断言

**注意**：Age断言需要专门的路由配置才能测试。可在application.yml中临时添加：

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Age断言测试路由（临时）
        - id: age_test_route
          uri: lb://service-product
          predicates:
            - Path=/product/**
            - Age=18,60
```

```powershell
# age=25（在18-60范围内）→ 匹配
curl "http://localhost:9000/product/1?age=25&token=test"

# age=10（低于18）→ 不匹配，404
curl "http://localhost:9000/product/1?age=10&token=test"

# age=70（超过60）→ 不匹配，404
curl "http://localhost:9000/product/1?age=70&token=test"
```

#### G7-G8: 自定义Log/Timer过滤器

**注意**：需在路由中配置Log和Timer过滤器：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product_route
          uri: lb://service-product
          predicates:
            - Path=/product/**
          filters:
            - Log=true      # 开启详细日志
            - Timer=3000     # 超过3000ms输出WARN
            - StripPrefix=0
```

```powershell
# 触发Log和Timer过滤器
curl http://localhost:9000/product/1?token=test
```

**预期日志输出**：
```
[LogFilter-Pre] 请求ID: xxx, 方法: GET, 路径: /product/1
[LogFilter-Post] 请求ID: xxx, 状态码: 200, 耗时: 50ms
[TimerFilter] 方法: GET, 路径: /product/1, 状态码: 200, 耗时: 50ms
```

---

### 5.5 全局鉴权测试（G9-G11）

#### G9: 携带token放行

```powershell
curl http://localhost:9000/product/1?token=abc123
```

**预期**：返回商品JSON

#### G10: 未携带token拒绝

```powershell
curl http://localhost:9000/product/1
```

**预期**：返回 `{"code":401,"msg":"Missing token"}`

#### G11: 白名单路径放行

```powershell
curl http://localhost:9000/user/1
```

**预期**：返回用户JSON（/user/**路径在白名单中，无需token）

---

### 5.6 JWT认证测试（G12-G14）

#### 生成测试JWT

```java
// 使用与Gateway一致的密钥生成JWT
String jwt = Jwts.builder()
    .setSubject("user123")
    .claim("authorities", "ROLE_USER,ROLE_ADMIN")
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
    .signWith(SignatureAlgorithm.HS256, "shop-gateway-secret-key-256bit".getBytes())
    .compact();
```

#### G12: 有效JWT

```powershell
curl -H "Authorization: Bearer <valid_jwt>" http://localhost:9000/product/1
```

**预期**：返回商品JSON，网关解析JWT后将X-User-Id和X-User-Authorities注入Header

#### G13: 过期JWT

```powershell
curl -H "Authorization: Bearer <expired_jwt>" http://localhost:9000/product/1
```

**预期**：返回 `{"code":401,"msg":"Token expired"}`

#### G14: 无效JWT

```powershell
curl -H "Authorization: Bearer invalid.token.here" http://localhost:9000/product/1
```

**预期**：返回 `{"code":401,"msg":"Invalid token"}`

---

### 5.7 Sentinel网关限流测试（G15-G17）

#### G15: API分组限流

**前提**：Gateway已启动，且已在Sentinel Dashboard中注册

```powershell
# 1. 访问Gateway的接口，使其在Sentinel Dashboard中注册
curl http://localhost:9000/product/1?token=test

# 2. 在Sentinel Dashboard中确认api-gateway服务已出现

# 3. 使用JMeter或ab工具对product_api施加>100 QPS的压力
ab -n 200 -c 20 "http://localhost:9000/product/1?token=test"
```

**预期结果**：
- 前100 QPS正常响应
- 超出部分返回429：`{"code":429,"msg":"访问过于频繁，请稍后重试"}`

#### G16: route维度限流

```powershell
# 对product_route施加>150 QPS的压力
ab -n 300 -c 30 "http://localhost:9000/product/port?token=test"
```

**预期**：超过150 QPS后被限流

#### G17: 自定义限流异常响应

```powershell
# 触发限流后查看响应
curl http://localhost:9000/product/1?token=test
# 限流时返回
# {"code":429,"msg":"访问过于频繁，请稍后重试"}
```

---

### 5.8 Feign拦截器传递用户信息测试（G18）

**目的**：验证网关注入的X-User-Id/X-User-Authorities能通过Feign拦截器传递到下游

```powershell
# 1. 携带JWT通过网关访问订单服务（订单服务会通过Feign调用商品服务）
curl -H "Authorization: Bearer <valid_jwt>" "http://localhost:9000/order/create?pid=1&uid=1&token=test"
```

**验证方法**：
1. 在商品服务的Controller中添加Header读取逻辑：
```java
@GetMapping("/{pid}")
public Product findById(@PathVariable Integer pid,
                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
    log.info(">>> 收到X-User-Id: {}", userId);
    ...
}
```
2. 检查商品服务日志，确认X-User-Id已传递

---

### 5.9 测试结果记录模板

| 编号 | 测试场景 | 预期结果 | 实际结果 | 是否通过 | 备注 |
|------|---------|---------|---------|---------|------|
| G1 | 商品路由转发 | 返回商品JSON（需token） | | □通过 □失败 | /product/**非白名单 |
| G2 | 订单路由转发 | 返回订单JSON | | □通过 □失败 | 需token |
| G3 | 用户路由转发 | 返回用户JSON | | □通过 □失败 | 白名单 |
| G4 | lb://负载均衡 | 端口交替出现 | | □通过 □失败 | 多实例 |
| G5 | 简写版路由 | 返回商品JSON | | □通过 □失败 | |
| G6 | Age断言 | 范围内匹配 | | □通过 □失败 | 需配置 |
| G7 | Log过滤器 | 日志输出 | | □通过 □失败 | 需配置 |
| G8 | Timer过滤器 | 耗时统计 | | □通过 □失败 | 需配置 |
| G9 | Auth-携带token | 放行 | | □通过 □失败 | |
| G10 | Auth-未携带token | 401 | | □通过 □失败 | |
| G11 | Auth-白名单 | 放行 | | □通过 □失败 | |
| G12 | JWT-有效 | 解析+注入Header | | □通过 □失败 | |
| G13 | JWT-过期 | 401 Token expired | | □通过 □失败 | |
| G14 | JWT-无效 | 401 Invalid token | | □通过 □失败 | |
| G15 | API分组限流 | 超100QPS限流 | | □通过 □失败 | 需压测 |
| G16 | route维度限流 | 超150QPS限流 | | □通过 □失败 | 需压测 |
| G17 | 限流异常响应 | JSON格式429 | | □通过 □失败 | |
| G18 | Feign传递用户信息 | Header传递到下游 | | □通过 □失败 | |

---

### 5.10 异常情况处理

| 异常现象 | 可能原因 | 解决方法 |
|---------|---------|---------|
| Gateway启动报Netty错误 | 引入了spring-boot-starter-web | 移除web依赖，Gateway基于WebFlux |
| 路由不生效 | predicates条件不匹配 | 检查Path配置和请求路径 |
| lb://路由503 | 服务未注册到Nacos | 确认目标服务已启动并注册 |
| 简写版路由404 | discovery.locator.enabled未开启 | 检查yml配置 |
| 自定义断言/过滤器不生效 | 类名不以规定后缀结尾 | 确保类名以RoutePredicateFactory/GatewayFilterFactory结尾 |
| Sentinel Dashboard看不到Gateway | 未访问过接口或通信端口被占 | 先访问接口触发注册，检查8722端口 |
| 网关限流不生效 | 规则未加载或QPS未超阈值 | 检查@PostConstruct执行日志，使用压测工具 |
| AuthGlobalFilter拦截所有请求 | 白名单配置不完整 | 调整isWhitelisted方法 |
| JWT解析失败 | 密钥不一致或JWT过期 | 检查SECRET_KEY配置，确认JWT未过期 |
| Feign拦截器Header丢失 | RequestContextHolder为null（异步线程） | 确保在Servlet线程中调用 |

---

## 六、完整配置示例

### 6.1 api-gateway/src/main/resources/application.yml

```yaml
server:
  port: 9000

spring:
  application:
    name: api-gateway
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: product_route
          uri: lb://service-product
          predicates:
            - Path=/product/**
          filters:
            - StripPrefix=0
        - id: order_route
          uri: lb://service-order
          predicates:
            - Path=/order/**
          filters:
            - StripPrefix=0
        - id: user_route
          uri: lb://service-user
          predicates:
            - Path=/user/**
          filters:
            - StripPrefix=0
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8722
      scg:
        fallback:
          mode: response
          response-status: 429
          response-body: '{"code":429,"msg":"网关限流，请稍后重试"}'

logging:
  level:
    com.example.shop.gateway: DEBUG
    org.springframework.cloud.gateway: INFO
```

---

## 七、与第三章/第四章的无缝集成

### 7.1 与Nacos的集成

- Gateway注册到Nacos（`spring-cloud-starter-alibaba-nacos-discovery`）
- 路由使用`lb://service-name`从Nacos获取服务实例
- 简写版通过`discovery.locator.enabled=true`自动路由
- 已有服务的Nacos配置保持不变

### 7.2 与Sentinel的集成

- Gateway集成Sentinel（`spring-cloud-starter-alibaba-sentinel` + `spring-cloud-alibaba-sentinel-gateway`）
- 通信端口8722，不与现有服务冲突（product:8719, order:8720, user:8721）
- Dashboard地址统一为localhost:8858
- 网关限流使用API分组维度，不影响各服务自身的Sentinel规则
- 已有服务的Sentinel配置保持不变

### 7.3 现有功能影响评估

| 现有功能 | 是否受影响 | 说明 |
|---------|-----------|------|
| 商品服务接口 | 否 | 通过Gateway可正常访问 |
| 订单服务+Feign调用 | 否 | FeignConfig增强Header传递 |
| 用户服务接口 | 否 | 通过Gateway可正常访问 |
| Nacos服务注册/发现 | 否 | Gateway也注册到Nacos |
| Sentinel流控/熔断 | 否 | 各服务自身规则不受影响 |
| Sentinel规则持久化 | 否 | 仅order服务配置持久化，不受影响 |

---

## 八、注意事项与最佳实践

1. **不能引入spring-boot-starter-web**：Gateway基于WebFlux(Netty)，引入Servlet容器会导致启动失败
2. **断言工厂命名规范**：类名必须以`RoutePredicateFactory`结尾，前缀为断言名称
3. **过滤器工厂命名规范**：类名必须以`GatewayFilterFactory`结尾，前缀为过滤器名称
4. **GlobalFilter优先级**：order值越小优先级越高，JWT认证(-100)先于鉴权(0)执行
5. **BlockRequestHandler返回值**：`Mono<ServerResponse>`而非`Mono<Void>`
6. **Feign拦截器线程安全**：`RequestContextHolder`仅在Servlet线程中有效，异步场景需特殊处理
7. **JWT密钥管理**：生产环境应通过配置中心或环境变量注入，严禁硬编码
8. **限流阈值调整**：代码中的初始QPS阈值仅作参考，应通过Sentinel Dashboard动态调整
9. **白名单维护**：AuthGlobalFilter中的白名单应从配置中心动态获取
10. **Gateway与前端部署**：生产环境建议Gateway部署在Nginx之后，Nginx处理SSL和静态资源
