# 第三章 Nacos Discovery——服务治理 实现总结

> 基于 Spring Boot 2.6.13 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0 + Nacos 2.2.0

---

## 一、章节技术要点分析

### 1.1 核心问题

第二章中服务间通过硬编码地址调用（`http://localhost:8081`），存在三大问题：

| 问题 | 说明 |
|------|------|
| 地址硬编码 | 服务地址变化需手工修改代码，重新部署 |
| 无法负载均衡 | 单点调用，无法分发流量到多个实例 |
| 维护困难 | 服务数量增多后，人工管理调用关系极易出错 |

### 1.2 解决方案：Nacos服务治理

第三章通过引入 **Nacos Discovery** 实现服务注册与发现，配合 **Spring Cloud LoadBalancer** 和 **OpenFeign** 完成服务治理的完整闭环：

```
服务注册 → 服务发现 → 负载均衡 → 声明式调用
  Nacos      Nacos    LoadBalancer    Feign
```

### 1.3 注册中心对比

| 注册中心 | CAP模型 | 健康检查方式 | 特点 |
|----------|---------|-------------|------|
| Zookeeper | CP | 长连接+Session | 强一致性，性能一般 |
| Eureka | AP | 心跳 | 自我保护机制，已停更 |
| Consul | CP | TCP/HTTP/gRPC | 支持多数据中心 |
| **Nacos** | **AP/CP** | **心跳+推送** | **同时支持AP和CP，功能最全** |

### 1.4 技术版本说明

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.6.13 | 基础框架 |
| Spring Cloud | 2021.0.5 | 微服务框架 |
| Spring Cloud Alibaba | 2021.0.5.0 | 阿里巴巴微服务组件 |
| Nacos Server | 2.2.0 | 服务注册与配置中心 |
| JDK | 1.8.0_202 | 运行环境 |

> **版本兼容性**：Spring Cloud Alibaba 2021.0.5.0 对应 Spring Boot 2.6.13 和 Spring Cloud 2021.0.5，不可随意升级单个组件版本。

---

## 二、实现步骤详解

### 2.1 服务注册与发现（3.3节）

#### 2.1.1 添加Maven依赖

三个微服务（shop-product、shop-order、shop-user）均添加以下依赖：

```xml
<!-- Nacos 服务注册与发现 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

shop-product 和 shop-order 额外添加 LoadBalancer（替代已移除的Ribbon）：

```xml
<!-- Spring Cloud LoadBalancer（替代已移除的Ribbon） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

shop-order 额外添加 OpenFeign：

```xml
<!-- OpenFeign 声明式服务调用 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

#### 2.1.2 主类添加注解

三个微服务的启动类均添加 `@EnableDiscoveryClient`：

```java
@SpringBootApplication
@EntityScan("com.example.shop.common.entity")
@EnableDiscoveryClient  // 开启Nacos服务注册与发现
public class ProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
```

`OrderApplication` 额外添加 `@EnableFeignClients` 和 `@LoadBalanced`：

```java
@SpringBootApplication
@EntityScan("com.example.shop.common.entity")
@EnableDiscoveryClient   // 开启Nacos服务注册与发现
@EnableFeignClients      // 开启Feign声明式服务调用
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    @Bean
    @LoadBalanced  // 使RestTemplate具备负载均衡能力
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

> `@EnableDiscoveryClient` 在 Spring Cloud 2021.x 中可省略（自动装配），但显式添加更清晰。

#### 2.1.3 配置Nacos地址

三个微服务的 `application.yml` 均添加Nacos配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # Nacos服务注册中心地址
```

#### 2.1.4 使用DiscoveryClient获取服务实例（3.3.3节知识点）

可通过 `DiscoveryClient` 手动获取服务实例列表：

```java
@Autowired
private DiscoveryClient discoveryClient;

// 获取服务实例列表
List<ServiceInstance> instances = discoveryClient.getInstances("service-product");
// 随机选择一个实例实现简单负载均衡
int index = new Random().nextInt(instances.size());
ServiceInstance instance = instances.get(index);
String url = instance.getHost() + ":" + instance.getPort();
```

> 此方式较原始，实际开发推荐使用 LoadBalancer 或 Feign。

---

### 2.2 负载均衡（3.4节）

#### 2.2.1 客户端负载均衡 vs 服务端负载均衡

| 类型 | 说明 | 典型组件 |
|------|------|----------|
| 服务端负载均衡 | 发生在服务提供者一方 | Nginx、F5 |
| **客户端负载均衡** | **发生在服务请求一方** | **Ribbon、LoadBalancer** |

微服务间调用一般选择客户端负载均衡。

#### 2.2.2 @LoadBalanced实现负载均衡

在 `OrderApplication` 中为 `RestTemplate` 添加 `@LoadBalanced` 注解：

```java
@Bean
@LoadBalanced  // 使RestTemplate具备负载均衡能力
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

添加后可直接使用服务名调用：

```java
// 使用服务名替代IP:端口
Product product = restTemplate.getForObject(
    "http://service-product/product/" + pid, Product.class);
```

#### 2.2.3 Ribbon → Spring Cloud LoadBalancer

Spring Cloud 2021.x 已移除 Ribbon，改用 **Spring Cloud LoadBalancer** 替代：

| 对比项 | Ribbon（已移除） | Spring Cloud LoadBalancer |
|--------|-----------------|--------------------------|
| 维护状态 | 已停更 | Spring Cloud官方维护 |
| 负载均衡策略 | 多种Rule可选 | 轮询（默认）、随机等 |
| 依赖 | spring-cloud-starter-netflix-ribbon | spring-cloud-starter-loadbalancer |
| 缓存支持 | 无 | 内置CachingServiceInstanceListSupplier |

可通过配置切换负载均衡策略：

```yaml
# 方式1: 全局配置随机策略（Spring Cloud LoadBalancer）
spring:
  cloud:
    loadbalancer:
      configurations: random

# 方式2: 针对指定服务配置（兼容旧配置格式）
service-product:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RandomRule
```

> **注意**：新项目建议直接使用 `spring-cloud-starter-loadbalancer`，不再使用Ribbon配置格式。

#### 2.2.4 LoadBalancer核心组件

Spring Cloud LoadBalancer 的核心工作流程：

```
ServiceInstanceListSupplier → 获取服务实例列表（从Nacos缓存）
    ↓
ReactorServiceInstanceLoadBalancer → 选择具体实例（轮询/随机）
    ↓
Response<ServiceInstance> → 返回选中的服务实例
```

关键组件说明：

| 组件 | 作用 | 默认实现 |
|------|------|----------|
| ServiceInstanceListSupplier | 提供实例列表 | CachingServiceInstanceListSupplier（带缓存） |
| ReactorLoadBalancer | 选择实例策略 | RoundRobinLoadBalancer（轮询） |
| LoadBalancerClient | 执行请求 | BlockingLoadBalancerClient |

---

### 2.3 基于Feign实现服务调用（3.5节）

#### 2.3.1 Feign简介

Feign 是 Spring Cloud 提供的**声明式伪 Http 客户端**，使得调用远程服务就像调用本地方法一样简单：

- 声明式调用，无需手动拼接URL
- 内置集成 LoadBalancer，默认实现负载均衡
- 支持超时配置、熔断降级等

#### 2.3.2 创建Feign客户端

新建 `shop-order/src/main/java/com/example/shop/order/feign/ProductFeignClient.java`：

```java
package com.example.shop.order.feign;

import com.example.shop.common.entity.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 商品服务Feign客户端
 * value: 目标服务在Nacos中注册的服务名
 * 内置集成了Spring Cloud LoadBalancer，默认实现负载均衡
 */
@FeignClient(value = "service-product")
public interface ProductFeignClient {

    /**
     * 根据商品ID查询商品信息
     * 方法签名需与商品微服务的ProductController.findById保持一致
     */
    @GetMapping("/product/{pid}")
    Product findByPid(@PathVariable("pid") Integer pid);

    /**
     * 查询商品服务实例端口（负载均衡验证用）
     */
    @GetMapping("/product/port")
    Map<String, Object> getPort();
}
```

**关键约束**：Feign接口的方法签名必须与目标服务的Controller方法签名保持一致。

#### 2.3.3 启用FeignClients

在 `OrderApplication` 主类添加注解：

```java
@EnableFeignClients  // 开启Feign声明式服务调用
```

#### 2.3.4 使用Feign客户端替代RestTemplate

修改 `OrderServiceImpl`：

```java
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private ProductFeignClient productFeignClient;  // 注入Feign客户端

    @Override
    public Order createOrder(Integer pid, Integer uid) {
        // 通过Feign客户端调用商品微服务（自动负载均衡）
        Product product = productFeignClient.findByPid(pid);
        log.info("查询到商品信息: {}", product);

        Order order = new Order();
        order.setUid(uid);
        order.setUsername("测试用户");
        order.setPid(pid);
        order.setPname(product.getPname());
        order.setPprice(product.getPprice());
        order.setNumber(1);

        orderDao.save(order);
        log.info("订单创建成功: {}", order);
        return order;
    }
}
```

#### 2.3.5 Feign超时配置

在 `shop-order/src/main/resources/application.yml` 中添加：

```yaml
# Feign超时配置
feign:
  client:
    config:
      default:
        connectTimeout: 5000   # 连接超时5秒
        readTimeout: 10000     # 读取超时10秒
```

> 也可针对特定服务单独配置超时，将 `default` 替换为目标服务名即可。

#### 2.3.6 Feign工作原理

```
调用方代码: productFeignClient.findByPid(pid)
    ↓
Feign动态代理 → 根据接口注解构造HTTP请求
    ↓
LoadBalancer → 从Nacos获取 service-product 实例列表 → 选择一个实例
    ↓
HTTP请求 → http://选中实例IP:端口/product/{pid}
    ↓
目标服务处理 → 返回结果
```

---

### 2.4 负载均衡验证接口设计

为直观验证负载均衡效果，在商品微服务和订单微服务中分别添加验证接口。

#### 2.4.1 商品微服务：端口上报接口

在 `ProductController` 中添加 `/product/port` 端点：

```java
@Value("${server.port}")
private String port;

@GetMapping("/{pid}")
public Product findById(@PathVariable Integer pid) {
    Product product = productService.findById(pid);
    // 输出端口号，便于观察负载均衡请求分发到哪个实例
    log.info(">>> 商品服务实例 [端口:{}] 处理查询请求, 商品ID: {}", port, pid);
    return product;
}

/**
 * 负载均衡验证接口：返回当前实例的端口号
 * 通过此接口可直观看到请求被分发到哪个商品服务实例
 */
@GetMapping("/port")
public Map<String, Object> getPort() {
    log.info(">>> 商品服务实例 [端口:{}] 收到端口查询请求", port);
    Map<String, Object> result = new HashMap<>();
    result.put("port", port);
    result.put("serviceName", "service-product");
    result.put("message", "请求到达端口 " + port + " 的商品服务实例");
    return result;
}
```

#### 2.4.2 订单微服务：负载均衡测试接口

在 `OrderController` 中添加 `/order/lb-test` 端点：

```java
@Autowired
private ProductFeignClient productFeignClient;

/**
 * 负载均衡验证接口：通过Feign调用商品服务的端口查询接口
 * 多次调用可观察请求被分发到不同商品服务实例
 */
@GetMapping("/lb-test")
public Map<String, Object> loadBalanceTest() {
    Map<String, Object> result = productFeignClient.getPort();
    log.info("负载均衡测试 - 请求分发到商品服务实例: {}", result);
    return result;
}
```

---

## 三、Nacos负载均衡验证方法详解

### 3.1 验证环境准备

#### 3.1.1 多实例部署方案

为验证负载均衡效果，需要启动多个商品微服务实例。通过 `--server.port` 参数覆盖默认端口：

| 实例 | 服务名 | 端口 | 启动命令 |
|------|--------|------|----------|
| 商品服务实例1 | service-product | 8081 | `java -jar shop-product-1.0-SNAPSHOT.jar` |
| 商品服务实例2 | service-product | 8082 | `java -jar shop-product-1.0-SNAPSHOT.jar --server.port=8082` |
| 用户服务 | service-user | 8071 | `java -jar shop-user-1.0-SNAPSHOT.jar` |
| 订单服务 | service-order | 8091 | `java -jar shop-order-1.0-SNAPSHOT.jar` |

启动顺序：**先启动商品服务两个实例 → 用户服务 → 订单服务**

```bash
# Step 1: 启动商品服务实例1（默认端口8081）
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar

# Step 2: 启动商品服务实例2（覆盖端口为8082）
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar --server.port=8082

# Step 3: 启动用户服务
java -jar shop-user/target/shop-user-1.0-SNAPSHOT.jar

# Step 4: 启动订单服务
java -jar shop-order/target/shop-order-1.0-SNAPSHOT.jar
```

#### 3.1.2 Nacos控制台确认注册

启动所有服务后，访问 Nacos 控制台 http://localhost:8848/nacos （账号 nacos/nacos），在 **服务列表** 中确认：

| 服务名 | 实例数 | 预期健康实例 |
|--------|--------|-------------|
| service-product | 2 | 8081, 8082 |
| service-user | 1 | 8071 |
| service-order | 1 | 8091 |

点击 `service-product` 查看实例详情，应显示两个健康实例。

### 3.2 负载均衡验证方法

#### 方法一：专用端口查询接口（推荐）

通过 `/order/lb-test` 接口直接观察请求分发：

```bash
# 连续调用5次
curl http://localhost:8091/order/lb-test
curl http://localhost:8091/order/lb-test
curl http://localhost:8091/order/lb-test
curl http://localhost:8091/order/lb-test
curl http://localhost:8091/order/lb-test
```

或使用浏览器/Postman重复访问 `http://localhost:8091/order/lb-test`。

**实际测试结果**（轮询策略）：

| 请求次数 | 返回端口 | 响应示例 |
|----------|----------|----------|
| 第1次 | 8081 | `{"port":"8081","serviceName":"service-product","message":"请求到达端口 8081 的商品服务实例"}` |
| 第2次 | 8082 | `{"port":"8082","serviceName":"service-product","message":"请求到达端口 8082 的商品服务实例"}` |
| 第3次 | 8081 | `{"port":"8081","serviceName":"service-product","message":"请求到达端口 8081 的商品服务实例"}` |
| 第4次 | 8082 | `{"port":"8082","serviceName":"service-product","message":"请求到达端口 8082 的商品服务实例"}` |
| 第5次 | 8081 | `{"port":"8081","serviceName":"service-product","message":"请求到达端口 8081 的商品服务实例"}` |

**结论**：Spring Cloud LoadBalancer 默认使用**轮询（Round Robin）**策略，请求在8081和8082之间交替分发。

#### 方法二：实际业务下单验证

通过下单接口验证负载均衡在实际业务中的效果：

```bash
# 多次下单，观察订单微服务和商品微服务的日志
curl "http://localhost:8091/order/create?pid=1&uid=1"
curl "http://localhost:8091/order/create?pid=2&uid=1"
curl "http://localhost:8091/order/create?pid=3&uid=1"
curl "http://localhost:8091/order/create?pid=1&uid=2"
curl "http://localhost:8091/order/create?pid=2&uid=2"
```

**日志验证**：在两个商品服务实例的控制台中观察日志输出：

```
# 实例1（8081）控制台输出：
>>> 商品服务实例 [端口:8081] 处理查询请求, 商品ID: 1
>>> 商品服务实例 [端口:8081] 处理查询请求, 商品ID: 3

# 实例2（8082）控制台输出：
>>> 商品服务实例 [端口:8082] 处理查询请求, 商品ID: 2
>>> 商品服务实例 [端口:8082] 处理查询请求, 商品ID: 1
```

#### 方法三：Nacos控制台观察

1. 访问 http://localhost:8848/nacos → 服务列表 → `service-product`
2. 查看实例列表，确认两个实例均为健康状态
3. 在调用过程中观察实例的请求计数变化

#### 方法四：直接访问商品服务实例

分别直接访问两个商品服务实例，确认各实例独立可用：

```bash
# 直接访问实例1
curl http://localhost:8081/product/port
# 返回: {"port":"8081","serviceName":"service-product","message":"请求到达端口 8081 的商品服务实例"}

# 直接访问实例2
curl http://localhost:8082/product/port
# 返回: {"port":"8082","serviceName":"service-product","message":"请求到达端口 8082 的商品服务实例"}
```

> 直接访问不经过负载均衡，仅用于确认各实例独立运行正常。

### 3.3 测试结果分析

#### 3.3.1 轮询策略验证

对5次连续请求的端口分布进行统计：

```
请求序列:  1    2    3    4    5
目标端口: 8081 8082 8081 8082 8081
```

| 统计项 | 值 |
|--------|-----|
| 总请求数 | 5 |
| 8081处理次数 | 3 |
| 8082处理次数 | 2 |
| 分发比例 | 3:2（趋近1:1） |

随着请求次数增加，分发比例将越来越趋近1:1，符合轮询策略的特征。

#### 3.3.2 负载均衡效果确认要点

验证负载均衡生效需确认以下条件：

| 条件 | 检查方法 | 预期 |
|------|----------|------|
| 服务注册成功 | Nacos控制台服务列表 | service-product 有2个健康实例 |
| 服务发现生效 | 订单服务日志无连接拒绝 | 正常返回商品信息 |
| 负载均衡分发 | 多次调用 /order/lb-test | 不同请求返回不同端口 |
| 轮询策略 | 连续多次调用 | 端口交替出现 |

#### 3.3.3 动态扩容验证

在服务运行期间新增商品服务实例，验证Nacos动态服务发现：

```bash
# 启动第三个商品服务实例
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar --server.port=8083
```

1. 等待约10秒（Nacos客户端默认拉取间隔）
2. Nacos控制台可见 service-product 实例数变为3
3. 再次连续调用 `/order/lb-test`，观察请求开始在8081/8082/8083三个端口间轮询

```bash
# 停止8081实例验证摘除
# 找到8081对应的PID并停止
# 等待心跳超时（默认15秒）后，Nacos自动剔除不健康实例
# 再次调用 /order/lb-test，请求仅分发到8082/8083
```

---

## 四、完整配置示例

### 4.1 各服务 application.yml 完整配置

#### shop-product/src/main/resources/application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: service-product
  datasource:
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost:3306/shop?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: onetwo12
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    database-platform: org.hibernate.dialect.MySQL5InnoDBDialect
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # Nacos服务注册中心地址
```

#### shop-order/src/main/resources/application.yml

```yaml
server:
  port: 8091

spring:
  application:
    name: service-order
  datasource:
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost:3306/shop?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: onetwo12
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    database-platform: org.hibernate.dialect.MySQL5InnoDBDialect
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # Nacos服务注册中心地址

# Feign超时配置
feign:
  client:
    config:
      default:
        connectTimeout: 5000   # 连接超时5秒
        readTimeout: 10000     # 读取超时10秒
```

#### shop-user/src/main/resources/application.yml

```yaml
server:
  port: 8071

spring:
  application:
    name: service-user
  datasource:
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost:3306/shop?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: onetwo12
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    database-platform: org.hibernate.dialect.MySQL5InnoDBDialect
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # Nacos服务注册中心地址
```

### 4.2 Nacos高级配置项

以下为生产环境常用的Nacos配置项，按需添加到 `spring.cloud.nacos.discovery` 下：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: public           # 命名空间，用于环境隔离（dev/test/prod）
        group: DEFAULT_GROUP        # 分组，用于更细粒度的服务隔离
        cluster-name: DEFAULT       # 集群名称，同集群优先调用
        weight: 1                   # 实例权重（0-100），权重越高分配流量越多
        ephemeral: true             # true=临时实例（客户端心跳），false=持久实例（服务端探测）
        heart-beat-interval: 5000   # 心跳间隔（毫秒），临时实例使用
        heart-beat-timeout: 15000   # 心跳超时（毫秒），超时标记不健康
        ip-delete-timeout: 30000    # IP删除超时（毫秒），超时剔除实例
        log-name: nacos-discovery   # 日志文件名
```

### 4.3 LoadBalancer策略配置

```yaml
spring:
  cloud:
    loadbalancer:
      configurations: random   # 全局使用随机策略（默认为轮询 round-robin）
      cache:
        ttl: 35s               # 实例列表缓存过期时间
        capacity: 256          # 缓存容量
      retry:
        enabled: true          # 开启重试（需添加spring-retry依赖）
```

---

## 五、修改文件清单

### 5.1 POM文件修改

| 文件 | 新增依赖 |
|------|----------|
| `shop-product/pom.xml` | nacos-discovery, loadbalancer, lombok |
| `shop-order/pom.xml` | nacos-discovery, loadbalancer, openfeign, lombok |
| `shop-user/pom.xml` | nacos-discovery |

### 5.2 主类修改

| 文件 | 新增注解 |
|------|----------|
| `shop-product/.../ProductApplication.java` | `@EnableDiscoveryClient` |
| `shop-order/.../OrderApplication.java` | `@EnableDiscoveryClient`, `@EnableFeignClients`, `@LoadBalanced` |
| `shop-user/.../UserApplication.java` | `@EnableDiscoveryClient` |

### 5.3 配置文件修改

| 文件 | 新增配置 |
|------|----------|
| `shop-product/src/main/resources/application.yml` | `spring.cloud.nacos.discovery.server-addr` |
| `shop-order/src/main/resources/application.yml` | `spring.cloud.nacos.discovery.server-addr`, `feign.client.config` |
| `shop-user/src/main/resources/application.yml` | `spring.cloud.nacos.discovery.server-addr` |

### 5.4 新增文件

| 文件 | 说明 |
|------|------|
| `shop-order/.../feign/ProductFeignClient.java` | 商品服务Feign声明式客户端接口 |

### 5.5 业务代码修改

| 文件 | 改动 |
|------|------|
| `shop-order/.../service/impl/OrderServiceImpl.java` | RestTemplate硬编码调用 → Feign声明式调用 |
| `shop-product/.../controller/ProductController.java` | 新增 `@Value("${server.port}")` 端口字段、日志输出端口号、`/product/port` 端点 |
| `shop-order/.../controller/OrderController.java` | 新增 `ProductFeignClient` 注入、`/order/lb-test` 端点 |

---

## 六、调用链路对比

### 6.1 第二章：硬编码调用

```
OrderService
  └─ restTemplate.getForObject("http://localhost:8081/product/" + pid)
       └─ 直接调用固定地址，无负载均衡
```

### 6.2 第三章：Feign + Nacos

```
OrderService
  └─ productFeignClient.findByPid(pid)
       ├─ Feign动态代理 → 构造HTTP请求
       ├─ Nacos服务发现 → 获取 service-product 实例列表
       ├─ LoadBalancer → 轮询选择一个实例
       └─ HTTP调用 → 请求目标商品微服务
```

### 6.3 调用方式演进总结

| 阶段 | 调用方式 | 地址维护 | 负载均衡 | 扩展性 |
|------|----------|----------|----------|--------|
| 第二章 | RestTemplate硬编码 | 代码中写死IP:端口 | 无 | 差 |
| 第三章(中间态) | @LoadBalanced + RestTemplate | 服务名替代IP | 有 | 一般 |
| 第三章(最终态) | Feign声明式调用 | 服务名自动解析 | 有 | 好 |

---

## 七、启动与验证

### 7.1 前置条件

1. MySQL 已启动，`shop` 数据库已创建
2. Nacos 2.2.0 已启动（单机模式）：

```bash
# Windows
startup.cmd -m standalone

# Linux/Mac
sh startup.sh -m standalone
```

3. 访问 Nacos 控制台：http://localhost:8848/nacos （默认账号 nacos/nacos）
4. 项目已编译打包：`mvn clean package -DskipTests`

### 7.2 启动顺序

```bash
# 1. 商品服务实例1（默认8081）
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar

# 2. 商品服务实例2（8082，用于负载均衡验证）
java -jar shop-product/target/shop-product-1.0-SNAPSHOT.jar --server.port=8082

# 3. 用户服务（8071）
java -jar shop-user/target/shop-user-1.0-SNAPSHOT.jar

# 4. 订单服务（8091）
java -jar shop-order/target/shop-order-1.0-SNAPSHOT.jar
```

### 7.3 完整验证流程

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| 1. 验证服务注册 | 访问Nacos控制台 → 服务列表 | 可见 service-product(2实例)、service-order、service-user |
| 2. 验证各实例独立可用 | `curl http://localhost:8081/product/1` 和 `curl http://localhost:8082/product/1` | 两个实例均返回商品信息 |
| 3. 验证服务发现 | `curl "http://localhost:8091/order/create?pid=1&uid=1"` | 返回订单信息，日志输出查询到的商品信息 |
| 4. 验证负载均衡-端口接口 | 连续5次 `curl http://localhost:8091/order/lb-test` | 端口在8081/8082间交替出现 |
| 5. 验证负载均衡-业务接口 | 连续多次下单，观察商品服务日志 | 两个实例交替处理请求 |
| 6. 验证动态扩容 | 启动第三个商品实例(8083)，再次调用 | 请求在3个实例间轮询 |
| 7. 验证故障摘除 | 停止8081实例，等待15秒后调用 | 请求仅分发到剩余实例 |

---

## 八、性能优化建议

### 8.1 Nacos客户端优化

| 优化项 | 默认值 | 建议值 | 说明 |
|--------|--------|--------|------|
| 心跳间隔 | 5000ms | 5000ms | 临时实例心跳频率，不建议调整过大 |
| 心跳超时 | 15000ms | 15000ms | 超时标记不健康，3次心跳未到则判定 |
| IP删除超时 | 30000ms | 30000ms | 不健康实例自动剔除时间 |
| 命名空间 | public | 按环境分离 | dev/test/prod使用不同namespace隔离 |

### 8.2 LoadBalancer优化

| 优化项 | 说明 | 配置方式 |
|--------|------|----------|
| 实例缓存 | LoadBalancer默认缓存实例列表35秒，减少Nacos查询频率 | `spring.cloud.loadbalancer.cache.ttl=35s` |
| 策略选择 | 高并发场景建议使用随机策略，避免轮询的热点问题 | `spring.cloud.loadbalancer.configurations=random` |
| 重试机制 | 开启失败重试，提高调用成功率 | `spring.cloud.loadbalancer.retry.enabled=true`（需spring-retry依赖） |

### 8.3 Feign优化

| 优化项 | 说明 | 配置示例 |
|--------|------|----------|
| 超时配置 | 根据业务RT设置合理超时，避免线程长时间阻塞 | `feign.client.config.default.connectTimeout=3000` |
| 连接池 | 默认使用URLConnection，建议替换为Apache HttpClient或OkHttp | 添加 `feign.httpclient.enabled=true` 依赖 |
| 日志级别 | 生产环境使用basic级别，避免full级别带来的性能开销 | `feign.client.config.default.loggerLevel=basic` |
| GZIP压缩 | 开启请求/响应压缩，减少网络传输量 | `feign.compression.request.enabled=true` |

**Feign连接池配置示例**：

```xml
<!-- 替换默认URLConnection为Apache HttpClient -->
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-httpclient</artifactId>
</dependency>
```

```yaml
feign:
  httpclient:
    enabled: true              # 启用Apache HttpClient
    max-connections: 200       # 最大连接数
    max-connections-per-route: 50  # 单个路由最大连接数
  compression:
    request:
      enabled: true            # 开启请求压缩
      mime-types: text/xml,application/xml,application/json
      min-request-size: 2048   # 最小压缩阈值
    response:
      enabled: true            # 开启响应压缩
```

### 8.4 服务部署优化

| 优化项 | 说明 |
|--------|------|
| 多实例部署 | 每个微服务至少部署2个实例，保证高可用 |
| 端口规划 | 统一规划端口范围，如商品808x、用户807x、订单809x |
| JVM参数 | 根据服务内存需求设置 `-Xms` 和 `-Xmx`，建议生产环境至少512m |
| 健康检查 | 配置Spring Boot Actuator健康端点，结合Nacos实现自动摘除 |

**启动参数优化示例**：

```bash
java -Xms256m -Xmx512m -jar shop-product-1.0-SNAPSHOT.jar \
  --server.port=8081 \
  --management.endpoints.web.exposure.include=health,info
```

---

## 九、常见问题与解决方案

### 9.1 服务注册失败

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| Nacos控制台看不到服务 | Nacos未启动或地址配置错误 | 确认Nacos已启动，检查 `server-addr` 配置 |
| 服务启动报 `Connection refused` | Nacos端口不可达 | 检查8848端口是否开放，防火墙是否放行 |
| 服务注册后很快消失 | 心跳超时，实例被剔除 | 检查网络连通性，调整心跳间隔参数 |
| 报 `NacosException: failed to req API` | 版本不兼容 | 确认Nacos Server版本与Client版本兼容 |

### 9.2 负载均衡问题

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| 请求始终访问同一个实例 | LoadBalancer缓存未更新 | 等待缓存过期（默认35s），或缩短缓存TTL |
| 启动多实例后仍只访问一个 | 第二个实例未成功注册 | 检查Nacos控制台确认实例数，检查8082实例日志 |
| 报 `UnknownServiceException` | LoadBalancer依赖缺失 | 确认已添加 `spring-cloud-starter-loadbalancer` 依赖 |
| 报 `Spring Cloud LoadBalancer is currently using the cache` | 缓存相关日志，非错误 | 正常提示，表示使用缓存提高性能 |

### 9.3 Feign调用问题

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| `FeignException: status 404` | 接口路径不匹配 | 检查Feign接口的 `@GetMapping` 路径与目标Controller完全一致 |
| `FeignException: status 405` | HTTP方法不匹配 | 确认Feign的 `@GetMapping`/`@PostMapping` 与目标一致 |
| `FeignException: Read timed out` | 读取超时 | 增大 `feign.client.config.default.readTimeout` 配置 |
| `UnsatisfiedDependencyException` 注入失败 | 未开启FeignClients | 确认启动类有 `@EnableFeignClients` 注解 |
| 参数传递丢失 | 未添加 `@RequestParam`/`@PathVariable` | Feign接口参数必须显式添加注解，不能省略 |

### 9.4 多实例启动问题

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| 端口冲突 | 多实例使用了相同端口 | 使用 `--server.port=xxxx` 覆盖默认端口 |
| 实例注册了但服务发现找不到 | 注册的namespace/group不一致 | 确保所有服务使用相同的namespace和group |
| 启动第二个实例时jar包被锁定 | Windows下进程锁文件 | 先停止第一个实例，或使用独立的jar副本 |
| 数据库连接数耗尽 | 多实例共享同一数据库 | 调整连接池大小，或使用HikariCP默认池化管理 |

### 9.5 Maven构建问题

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| `package` 失败，Lombok找不到 | shop-product未添加Lombok依赖 | 在pom.xml中添加Lombok依赖 |
| jar文件被锁定无法覆盖 | 服务运行中锁定了jar文件 | 先停止所有服务再打包 |
| 依赖版本冲突 | 单个组件版本升级导致不兼容 | 严格使用BOM管理版本，不单独覆盖 |

---

## 十、注意事项与最佳实践

1. **版本兼容性**：Spring Cloud Alibaba 2021.0.5.0 对应 Spring Boot 2.6.13 和 Spring Cloud 2021.0.5，不可随意升级单个组件版本
2. **Ribbon已移除**：Spring Cloud 2021.x 已移除 Ribbon，必须使用 `spring-cloud-starter-loadbalancer` 替代
3. **Feign方法签名**：接口方法的请求路径、参数注解必须与目标Controller完全一致，否则调用失败
4. **Nacos临时实例**：默认注册为临时实例（客户端心跳），不健康时自动剔除；持久实例由服务端探测，不剔除仅标记
5. **超时配置**：生产环境建议根据实际业务响应时间调整 Feign 超时，避免因超时过短导致不必要的重试
6. **命名空间隔离**：不同环境（dev/test/prod）使用不同namespace，避免开发环境影响测试/生产
7. **集群优先**：配置 `cluster-name` 后，LoadBalancer优先调用同集群实例，减少跨机房延迟
8. **权重调整**：通过Nacos控制台动态调整实例权重，实现灰度发布和流量控制
9. **健康检查**：结合Spring Boot Actuator的 `/actuator/health` 端点，实现更精准的健康状态判断
10. **日志规范**：在关键调用点输出实例端口号，便于线上排查负载均衡问题
