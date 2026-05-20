# 第六章 Sleuth——链路追踪 实现总结

> 基于 Spring Boot 2.6.13 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0

---

## 一、第六章技术要点分析

### 1.1 核心问题

微服务架构下，一次请求可能跨越多个服务，当出现延迟或错误时，难以快速定位问题所在：

| 问题 | 说明 |
|------|------|
| 调用链不透明 | 请求经过多个微服务，无法直观看到完整调用路径 |
| 故障定位困难 | 某个服务出现延迟，无法判断是哪个服务导致的 |
| 性能瓶颈难找 | 无法量化每个服务的响应时间贡献 |
| 日志关联困难 | 各服务独立输出日志，无法将同一次请求的日志串联 |

### 1.2 解决方案：Sleuth + Zipkin

```
请求进入 → Gateway
              ├── Sleuth自动注入traceId/spanId到HTTP Header
              ├── 调用商品服务 → traceId自动传递
              ├── 调用订单服务 → traceId自动传递
              │     └── Feign调用商品服务 → traceId通过Header自动传递
              └── 各服务将Span数据上报到Zipkin Server
                    └── Zipkin存储到Elasticsearch → Kibana可视化查询
```

### 1.3 核心概念

| 概念 | 说明 |
|------|------|
| **Trace** | 一次完整的请求链路，由相同的traceId串联 |
| **Span** | 基本工作单元，一个Span表示一次服务调用 |
| **Annotation** | 记录事件时间点：cs(客户端发送)、sr(服务端接收)、ss(服务端发送)、cr(客户端接收) |
| **traceId** | 全链路唯一标识，在整个调用链中保持一致 |
| **spanId** | 当前Span的唯一标识 |
| **parentId** | 父Span的ID，用于构建调用树 |

### 1.4 技术版本说明

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Cloud Sleuth | 3.1.5 | 由spring-cloud-dependencies BOM管理 |
| Spring Cloud Sleuth Zipkin | 3.1.5 | Zipkin客户端集成，由BOM管理 |
| Zipkin Server | 2.23.2（用户使用2.3.18） | 链路追踪数据收集与展示 |
| Elasticsearch | 7.17.2 | Zipkin数据持久化存储 |
| Kibana | 7.17.2 | 日志与追踪数据可视化 |

> **版本兼容性**：Spring Cloud 2021.0.5 BOM已包含Sleuth 3.1.x版本管理，无需在子模块中指定版本号。

---

## 二、实现步骤详解

### 2.1 父POM添加Sleuth版本属性

在父POM的`<properties>`中添加Sleuth版本属性（由BOM管理，显式声明便于维护）：

```xml
<properties>
    ...
    <spring-cloud-sleuth.version>3.1.5</spring-cloud-sleuth.version>
</properties>
```

> **说明**：`spring-cloud-dependencies` BOM已管理Sleuth版本，子模块无需指定版本号。添加此属性是为了文档清晰和可能的版本覆盖需求。

### 2.2 各微服务添加Sleuth+Zipkin依赖

四个微服务（shop-product、shop-order、shop-user、api-gateway）均添加：

```xml
<!-- Sleuth + Zipkin 链路追踪 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

**依赖说明**：

| 依赖 | 作用 |
|------|------|
| `spring-cloud-starter-sleuth` | 自动埋点，生成traceId/spanId，注入到日志MDC和HTTP Header |
| `spring-cloud-sleuth-zipkin` | 将Span数据通过HTTP上报到Zipkin Server |

> **Gateway说明**：Gateway基于WebFlux(Reactive)，`spring-cloud-starter-sleuth`自动适配Reactive环境，无需额外配置。但Gateway不能使用`spring-cloud-starter-zipkin`（已废弃），应使用`spring-cloud-sleuth-zipkin`。

### 2.3 配置Zipkin和Sleuth

四个微服务的`application.yml`均添加：

```yaml
spring:
  # Zipkin链路追踪配置
  zipkin:
    base-url: http://127.0.0.1:9411    # Zipkin Server地址
    sender:
      type: web                         # 使用HTTP方式发送追踪数据到Zipkin
  # Sleuth采样配置
  sleuth:
    sampler:
      probability: 1.0                  # 采样百分比（1.0=100%全采集，生产建议0.1~0.5）
```

**配置项说明**：

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.zipkin.base-url` | `http://127.0.0.1:9411` | Zipkin Server地址 |
| `spring.zipkin.sender.type` | `web` | 使用HTTP发送追踪数据（替代RabbitMQ/Kafka等） |
| `spring.sleuth.sampler.probability` | `1.0` | 采样率100%（开发环境全采集，生产建议0.1~0.5） |

### 2.4 配置logback-spring.xml（MDC输出traceId/spanId）

Sleuth自动将`traceId`和`spanId`放入SLF4J的MDC（Mapped Diagnostic Context），在日志配置中引用即可。

四个微服务均添加`src/main/resources/logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId},%X{spanId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**日志输出效果**：

```
2025-05-11 10:23:45.678 [http-nio-8081-exec-1] INFO [a1b2c3d4e5f67890,a1b2c3d4e5f67890] - >>> 商品服务实例 [端口:8081] 处理查询请求, 商品ID: 1
```

其中`[a1b2c3d4e5f67890,a1b2c3d4e5f67890]`就是`[traceId,spanId]`。

### 2.5 Zipkin数据持久化到Elasticsearch

Zipkin默认使用内存存储，重启后数据丢失。使用Elasticsearch实现持久化：

**启动Zipkin时指定Elasticsearch存储**：

```bash
java -jar zipkin-server-2.23.2-exec.jar --STORAGE_TYPE=elasticsearch --ES_HOSTS=http://localhost:9200
```

或使用Docker：

```bash
docker run -d -p 9411:9411 \
  -e STORAGE_TYPE=elasticsearch \
  -e ES_HOSTS=http://localhost:9200 \
  openzipkin/zipkin:2.23
```

**Kibana可视化**：

访问 `http://localhost:5601`，在Dev Tools中使用以下查询：

```
GET zipkin:span-*/_search
{
  "query": {
    "match": {
      "traceId": "a1b2c3d4e5f67890"
    }
  }
}
```

---

## 三、实现清单

### 3.1 依赖变更

| 模块 | 新增依赖 |
|------|---------|
| shop-product | `spring-cloud-starter-sleuth`, `spring-cloud-sleuth-zipkin` |
| shop-order | `spring-cloud-starter-sleuth`, `spring-cloud-sleuth-zipkin` |
| shop-user | `spring-cloud-starter-sleuth`, `spring-cloud-sleuth-zipkin` |
| api-gateway | `spring-cloud-starter-sleuth`, `spring-cloud-sleuth-zipkin` |

### 3.2 配置变更

| 服务 | 新增配置项 |
|------|-----------|
| shop-product | `spring.zipkin.base-url`, `spring.zipkin.sender.type`, `spring.sleuth.sampler.probability` |
| shop-order | `spring.zipkin.base-url`, `spring.zipkin.sender.type`, `spring.sleuth.sampler.probability` |
| shop-user | `spring.zipkin.base-url`, `spring.zipkin.sender.type`, `spring.sleuth.sampler.probability` |
| api-gateway | `spring.zipkin.base-url`, `spring.zipkin.sender.type`, `spring.sleuth.sampler.probability` |

### 3.3 新增文件清单

| 文件 | 模块 | 功能 |
|------|------|------|
| `logback-spring.xml` | shop-product | 日志配置，MDC输出traceId/spanId |
| `logback-spring.xml` | shop-order | 日志配置，MDC输出traceId/spanId |
| `logback-spring.xml` | shop-user | 日志配置，MDC输出traceId/spanId |
| `logback-spring.xml` | api-gateway | 日志配置，MDC输出traceId/spanId |

### 3.4 修改文件清单

| 文件 | 变更内容 |
|------|---------|
| `pom.xml`(父POM) | `<properties>`新增`spring-cloud-sleuth.version` |
| `shop-product/pom.xml` | 新增sleuth+zipkin依赖 |
| `shop-order/pom.xml` | 新增sleuth+zipkin依赖 |
| `shop-user/pom.xml` | 新增sleuth+zipkin依赖 |
| `api-gateway/pom.xml` | 新增sleuth+zipkin依赖 |
| `shop-product/application.yml` | 新增zipkin+sleuth配置 |
| `shop-order/application.yml` | 新增zipkin+sleuth配置 |
| `shop-user/application.yml` | 新增zipkin+sleuth配置 |
| `api-gateway/application.yml` | 新增zipkin+sleuth配置 |

---

## 四、关键设计决策

1. **采样率1.0（100%）**：开发环境全采集便于调试，生产建议0.1~0.5，降低性能开销
2. **sender类型使用web**：使用HTTP方式发送Span数据到Zipkin，简单直接，适合中小规模部署；大规模生产环境可考虑RabbitMQ/Kafka异步发送
3. **所有微服务均集成Sleuth+Zipkin**：包括Gateway，实现全链路追踪覆盖
4. **Gateway使用spring-cloud-starter-sleuth**：Sleuth自动适配WebFlux Reactive环境，无需额外配置
5. **logback-spring.xml替代application.yml日志配置**：MDC中的`%X{traceId}`和`%X{spanId}`需要通过logback模式配置才能输出
6. **Zipkin数据持久化使用Elasticsearch**：性能优于MySQL，适合高吞吐量场景
7. **Sleuth自动传播traceId**：通过HTTP Header自动在Feign调用和Gateway转发中传播，无需手动编写拦截器
8. **Feign无需额外配置**：Sleuth自动通过Feign的RequestInterceptor传播traceId/spanId，与已有的FeignConfig（传递X-User-Id/X-User-Authorities）互不影响

---

## 五、Sleuth自动传播原理

### 5.1 同线程传播（Servlet微服务）

```
请求进入（携带traceId Header）
    ↓
Sleuth Filter → 从Header提取traceId，放入当前线程的TraceContext
    ↓
业务代码执行 → MDC.get("traceId") 可获取
    ↓
Feign调用 → Sleuth自动注册RequestInterceptor，将traceId写入请求Header
    ↓
下游服务 → Sleuth Filter从Header提取traceId，继续传播
```

### 5.2 Reactive传播（Gateway）

```
请求进入Gateway
    ↓
Sleuth WebFlux Filter → 从Header提取traceId，放入Reactor Context
    ↓
Gateway Filter链 → 通过Reactor Context传播traceId
    ↓
转发请求 → Sleuth自动将traceId写入下游请求Header
```

### 5.3 手动获取TraceId

```java
import org.slf4j.MDC;

// 在任意业务代码中获取当前traceId
String traceId = MDC.get("traceId");

// 用于日志关联、返回给前端等场景
log.info("当前请求traceId: {}", traceId);
```

---

## 六、与第三章/第四章/第五章的无缝集成

### 6.1 与Nacos的集成

| 集成点 | 说明 |
|--------|------|
| 服务名一致性 | Sleuth使用`spring.application.name`作为服务名，与Nacos注册名一致 |
| 配置位置 | zipkin/sleuth配置与nacos配置在同一个`spring.cloud`下，互不干扰 |

### 6.2 与Sentinel的集成

| 集成点 | 说明 |
|--------|------|
| 互不影响 | Sleuth做链路追踪（可观测性），Sentinel做流量控制（稳定性），功能维度不同 |
| 日志增强 | Sleuth在日志中输出traceId，配合Sentinel的BlockException日志，可快速定位限流请求 |
| 无配置冲突 | 两者的配置项在不同命名空间下，不存在冲突 |

### 6.3 与Gateway的集成

| 集成点 | 说明 |
|--------|------|
| Gateway自动传播 | Sleuth自动在Gateway转发时传播traceId到下游Header |
| Feign调用传播 | Sleuth自动在Feign调用时传播traceId，与FeignConfig互不干扰 |
| WebFlux兼容 | `spring-cloud-starter-sleuth`自动适配Reactive环境 |

### 6.4 现有功能影响评估

| 现有功能 | 是否受影响 | 说明 |
|---------|-----------|------|
| 商品服务接口 | 否 | Sleuth透明增强，不改变业务逻辑 |
| 订单服务+Feign调用 | 否 | Sleuth自动传播traceId，不干扰已有FeignConfig |
| 用户服务接口 | 否 | Sleuth透明增强 |
| Gateway路由转发 | 否 | Sleuth自动适配WebFlux |
| Nacos服务注册/发现 | 否 | 配置互不干扰 |
| Sentinel流控/熔断 | 否 | 功能维度不同，互不影响 |
| Sentinel规则持久化 | 否 | 仅order服务配置持久化，不受影响 |

---

## 七、全面手动测试指南

### 7.1 测试环境准备

#### 7.1.1 基础设施检查清单

| 组件 | 地址 | 验证方法 |
|------|------|---------|
| MySQL | localhost:3306 | `mysql -u root -p -e "SELECT 1"` |
| Nacos Server | localhost:8848 | 浏览器访问 http://localhost:8848/nacos |
| Sentinel Dashboard | localhost:8858 | 浏览器访问 http://localhost:8858 |
| **Zipkin Server** | **localhost:9411** | **浏览器访问 http://localhost:9411** |
| **Elasticsearch** | **localhost:9200** | **浏览器访问 http://localhost:9200** |
| **Kibana** | **localhost:5601** | **浏览器访问 http://localhost:5601** |

#### 7.1.2 Zipkin启动命令

**方式一：内存存储（开发测试）**

```bash
java -jar zipkin-server-2.23.2-exec.jar
```

**方式二：Elasticsearch持久化（推荐）**

```bash
java -jar zipkin-server-2.23.2-exec.jar --STORAGE_TYPE=elasticsearch --ES_HOSTS=http://localhost:9200
```

#### 7.1.3 微服务启动顺序

```
1. ProductApplication → 端口8081
2. UserApplication → 端口8071
3. OrderApplication → 端口8091
4. GatewayApplication → 端口9000
```

### 7.2 核心功能测试点

| 编号 | 测试场景 | 测试目标 | 关键验证点 |
|------|---------|---------|-----------|
| S1 | 日志输出traceId/spanId | Sleuth MDC | 日志中出现traceId和spanId |
| S2 | 单服务追踪 | Zipkin UI | Zipkin中可看到单服务Span |
| S3 | 跨服务调用链追踪 | Sleuth传播 | Zipkin中看到完整调用链 |
| S4 | Gateway→微服务追踪 | Gateway传播 | 通过Gateway的请求链路完整 |
| S5 | Elasticsearch持久化 | 数据持久化 | Zipkin重启后数据仍存在 |
| S6 | 采样率验证 | 采样控制 | probability=1.0时100%采集 |

### 7.3 测试步骤

#### S1: 日志输出traceId/spanId验证

```powershell
# 访问商品服务
curl http://localhost:8081/product/1
```

**预期**：商品服务控制台日志输出包含`[traceId,spanId]`：

```
2025-05-11 10:23:45.678 [http-nio-8081-exec-1] INFO [a1b2c3d4e5f67890,a1b2c3d4e5f67890] - >>> 商品服务实例 [端口:8081] 处理查询请求, 商品ID: 1
```

> **注意**：非请求线程的日志（如启动日志），traceId和spanId显示为空`[,]`，这是正常现象。

#### S2: 单服务追踪验证

```powershell
# 访问商品服务
curl http://localhost:8081/product/1

# 打开Zipkin UI
# 浏览器访问 http://localhost:9411
# 点击"Run Query"查询追踪记录
```

**预期**：
- Zipkin UI中可看到一条trace记录
- 服务名为`service-product`
- Span数量为1（仅商品服务自身的Span）

#### S3: 跨服务调用链追踪（核心验证）

```powershell
# 通过订单服务创建订单（会Feign调用商品服务）
curl "http://localhost:8091/order/create?pid=1&uid=1"
```

**预期**：
- Zipkin UI中可看到一条包含2个Span的trace
- 调用链：`service-order → service-product`
- 两个Span的traceId相同，表示属于同一次请求
- 订单服务的日志和商品服务的日志中traceId一致

**日志验证**：

```
# 订单服务日志：
2025-05-11 10:23:45.678 [http-nio-8091-exec-1] INFO [a1b2c3d4e5f67890,a1b2c3d4e5f67890] - 收到下单请求, 商品ID: 1, 用户ID: 1
2025-05-11 10:23:45.700 [http-nio-8091-exec-1] INFO [a1b2c3d4e5f67890,abcdef1234567890] - 查询到商品信息: Product(...)

# 商品服务日志（同一次请求，traceId一致）：
2025-05-11 10:23:45.690 [http-nio-8081-exec-1] INFO [a1b2c3d4e5f67890,1122334455667788] - >>> 商品服务实例 [端口:8081] 处理查询请求, 商品ID: 1
```

#### S4: Gateway→微服务追踪

```powershell
# 通过网关访问商品服务
curl http://localhost:9000/product/1?token=test

# 通过网关创建订单（Gateway → Order → Product）
curl "http://localhost:9000/order/create?pid=1&uid=1&token=test"
```

**预期**：
- 网关访问商品服务：Zipkin显示3个Span（gateway → service-product）
- 网关创建订单：Zipkin显示4个Span（gateway → service-order → service-product）
- 整个调用链的traceId一致

#### S5: Elasticsearch持久化验证

```powershell
# 1. 先产生一些追踪数据
curl "http://localhost:8091/order/create?pid=1&uid=1"
curl "http://localhost:8081/product/1"

# 2. 在Zipkin UI中确认数据存在

# 3. 重启Zipkin Server
# 停止Zipkin进程后重新启动（使用ES存储模式）

# 4. 再次打开Zipkin UI查询
```

**预期**：重启Zipkin后，之前的追踪数据仍然可以查询到。

**Kibana验证**（可选）：

访问 http://localhost:5601 → Dev Tools：

```
GET _cat/indices/zipkin*?v

GET zipkin:span-*/_search
{
  "size": 10,
  "sort": [{"timestamp_millis": "desc"}]
}
```

#### S6: 采样率验证

临时将某个服务的采样率改为0.0：

```yaml
spring:
  sleuth:
    sampler:
      probability: 0.0  # 不采集
```

**预期**：该服务的Span不再上报到Zipkin。

---

### 7.4 测试结果记录模板

| 编号 | 测试场景 | 预期结果 | 实际结果 | 是否通过 | 备注 |
|------|---------|---------|---------|---------|------|
| S1 | 日志traceId/spanId | 日志格式包含traceId | | □通过 □失败 | |
| S2 | 单服务追踪 | Zipkin显示1个Span | | □通过 □失败 | |
| S3 | 跨服务调用链 | Zipkin显示完整链路 | | □通过 □失败 | order→product |
| S4 | Gateway链路追踪 | 包含gateway的链路 | | □通过 □失败 | gateway→order→product |
| S5 | ES持久化 | 重启后数据仍在 | | □通过 □失败 | 需ES模式启动 |
| S6 | 采样率控制 | probability=0时不采集 | | □通过 □失败 | |

---

### 7.5 异常情况处理

| 异常现象 | 可能原因 | 解决方法 |
|---------|---------|---------|
| 日志中没有traceId | 未引入sleuth依赖 | 检查pom.xml是否添加`spring-cloud-starter-sleuth` |
| 日志traceId为空`[,]` | 该日志不在请求线程中 | 正常现象，仅请求处理线程有traceId |
| Zipkin UI看不到数据 | 1. Zipkin未启动 2. base-url配置错误 3. 采样率为0 | 确认Zipkin运行在9411端口，检查配置 |
| 跨服务traceId不一致 | Feign未自动传播 | 确认sleuth依赖正确，检查Feign配置 |
| Gateway链路断裂 | Gateway未引入sleuth | 确认api-gateway的pom.xml和yml配置 |
| ES持久化失败 | 1. ES未启动 2. 启动参数错误 | 确认ES在9200端口，使用`--STORAGE_TYPE=elasticsearch --ES_HOSTS` |
| 启动报Sleuth版本冲突 | 手动指定了不兼容版本 | 移除子模块中的版本号，使用BOM管理 |
| Zipkin数据堆积 | ES索引未清理 | 配置ES索引生命周期管理(ILM)或定期清理 |

---

## 八、完整配置示例

### 8.1 shop-product/src/main/resources/application.yml

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
  # Zipkin链路追踪配置
  zipkin:
    base-url: http://127.0.0.1:9411
    sender:
      type: web
  # Sleuth采样配置
  sleuth:
    sampler:
      probability: 1.0
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8719
      web-context-unify: false
```

### 8.2 shop-order/src/main/resources/application.yml

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
  # Zipkin链路追踪配置
  zipkin:
    base-url: http://127.0.0.1:9411
    sender:
      type: web
  # Sleuth采样配置
  sleuth:
    sampler:
      probability: 1.0
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    sentinel:
      transport:
        dashboard: localhost:8858
        port: 8720
      web-context-unify: false
      datasource:
        flow:
          nacos:
            server-addr: 127.0.0.1:8848
            dataId: ${spring.application.name}-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow
        degrade:
          nacos:
            server-addr: 127.0.0.1:8848
            dataId: ${spring.application.name}-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade

# Feign超时配置 + Sentinel整合
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
  sentinel:
    enabled: true
```

### 8.3 api-gateway/src/main/resources/application.yml

```yaml
server:
  port: 9000

spring:
  application:
    name: api-gateway
  # Zipkin链路追踪配置
  zipkin:
    base-url: http://127.0.0.1:9411
    sender:
      type: web
  # Sleuth采样配置
  sleuth:
    sampler:
      probability: 1.0
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
            - Log=true
            - Timer=3000
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

## 九、注意事项与最佳实践

1. **采样率设置**：开发环境使用`probability: 1.0`全采集便于调试；生产环境建议`0.1~0.5`，降低性能开销
2. **Zipkin Server部署**：生产环境建议集群部署，使用Elasticsearch作为后端存储，避免内存存储数据丢失
3. **Gateway兼容性**：Gateway基于WebFlux，Sleuth自动适配Reactive环境，无需额外配置
4. **Feign自动传播**：Sleuth自动通过Feign的RequestInterceptor传播traceId，与业务自定义的FeignConfig互不干扰
5. **手动获取TraceId**：通过`MDC.get("traceId")`获取，可用于异常返回、日志关联等场景
6. **ES索引管理**：Zipkin默认按天创建ES索引（`zipkin:span-yyyy-mm-dd`），建议配置ILM策略定期清理旧索引
7. **版本兼容性**：Spring Cloud Alibaba 2021.0.5.0对应Sleuth 3.1.x，不要单独升级Sleuth版本
8. **spring-cloud-starter-zipkin已废弃**：Spring Cloud 2021.x中应使用`spring-cloud-sleuth-zipkin`替代
9. **Kibana日志聚合**：通过traceId在Kibana中搜索，可快速聚合同一次请求在所有服务中产生的日志
10. **与SkyWalking对比**：Sleuth+Zipkin对代码有侵入（需引入依赖），SkyWalking基于Java Agent无侵入；新项目或K8s环境可考虑SkyWalking
