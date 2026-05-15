# 第四章 Sentinel服务容错 — 实现总结

## 一、第四章技术要点分析

### 1.1 核心架构设计

Sentinel作为分布式系统的流量防卫兵，以**流量为切入点**，从多个维度保护服务稳定性：

```
┌──────────────────────────────────────────────────┐
│                 Sentinel Dashboard                │
│              (控制台: localhost:8858)              │
│   规则配置 │ 实时监控 │ 集群点 │ 流控规则管理      │
└─────────────────┬────────────────────────────────┘
                  │ 通信端口（8719/8720/8721）
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼───┐   ┌───▼───┐   ┌───▼───┐
│Product│   │ Order │   │ User  │
│:8719  │   │:8720  │   │:8721  │
└───┬───┘   └───┬───┘   └───┬───┘
    │            │            │
    └────────────┼────────────┘
                 │
        ┌────────▼────────┐
        │  Nacos Server   │
        │  :8848          │
        │  (注册中心+配置) │
        └─────────────────┘
```

### 1.2 五大规则体系

| 规则类型 | 作用 | 关键参数 |
|---------|------|---------|
| 流控规则 | 控制QPS/线程数 | 阈值类型、流控模式(直接/关联/链路)、流控效果(快速失败/Warm Up/排队等待) |
| 降级规则 | 熔断降级策略 | 平均响应时间、异常比例、异常数 |
| 热点规则 | 参数级限流 | 参数索引、单机阈值、参数例外项 |
| 授权规则 | 来源访问控制 | 白名单/黑名单、RequestOriginParser |
| 系统规则 | 应用级保护 | Load、RT、线程数、入口QPS、CPU使用率 |

### 1.3 @SentinelResource注解设计

```
请求进入 → Sentinel拦截检查
    ├── 触发BlockException → blockHandler处理（限流/熔断/系统保护）
    └── 业务异常(Throwable) → fallback处理（运行时异常兜底）
```

## 二、实现清单

### 2.1 依赖变更

| 模块 | 新增依赖 |
|------|---------|
| shop-product | `spring-cloud-starter-alibaba-sentinel`, `spring-boot-starter-test` |
| shop-order | `spring-cloud-starter-alibaba-sentinel`, `sentinel-datasource-nacos`, `spring-boot-starter-test` |
| shop-user | `spring-cloud-starter-alibaba-sentinel`, `lombok` |

### 2.2 配置变更

**三个服务Sentinel通信端口错开**（避免冲突）：

| 服务 | 应用端口 | Sentinel通信端口 | Dashboard地址 |
|------|---------|-----------------|-------------|
| shop-product | 8081 | 8719 | localhost:8858 |
| shop-order | 8091 | 8720 | localhost:8858 |
| shop-user | 8071 | 8721 | localhost:8858 |

**order服务额外配置**：
- `feign.sentinel.enabled=true`：开启Feign整合Sentinel
- `sentinel.datasource.flow/degrade`：规则持久化到Nacos（示范）

### 2.3 新增文件清单

| 文件 | 模块 | 功能 |
|------|------|------|
| `ProductBlockHandler.java` | shop-product | 商品服务@SentinelResource的blockHandler（限流/熔断处理） |
| `ProductFallbackHandler.java` | shop-product | 商品服务@SentinelResource的fallback（业务异常降级） |
| `CustomUrlBlockHandler.java` | shop-product | 商品服务URL级别自定义异常返回（5种BlockException分类处理） |
| `ProductFeignClientFallbackFactory.java` | shop-order | Feign整合Sentinel的FallbackFactory（可获取异常详情） |
| `CustomUrlBlockHandler.java` | shop-order | 订单服务URL级别自定义异常返回 |
| `RequestOriginParserDefinition.java` | shop-order | 授权规则请求来源解析器 |
| `CustomUrlBlockHandler.java` | shop-user | 用户服务URL级别自定义异常返回 |
| `ProductBlockHandlerTest.java` | shop-product | blockHandler单元测试（4个用例） |
| `ProductFallbackHandlerTest.java` | shop-product | fallback单元测试（2个用例） |
| `ProductFeignClientFallbackFactoryTest.java` | shop-order | FallbackFactory单元测试（3个用例） |
| `RequestOriginParserDefinitionTest.java` | shop-order | 请求来源解析器单元测试（3个用例） |

### 2.4 修改文件清单

| 文件 | 变更内容 |
|------|---------|
| `ProductController.java` | 添加`@SentinelResource`注解 + 秒杀热点接口 |
| `ProductFeignClient.java` | 添加`fallbackFactory`属性指向FallbackFactory |
| 三个模块`pom.xml` | 添加sentinel/test依赖 |
| 三个模块`application.yml` | 添加sentinel配置（dashboard:8858, 端口错开） |

## 三、关键设计决策

1. **Dashboard端口8858**：文档默认8080，用户指定改为8858
2. **Sentinel通信端口错开**：product:8719, order:8720, user:8721，避免同一主机多服务冲突
3. **Feign选择fallbackFactory**：相比fallback可获取具体异常信息（Throwable），便于排查问题
4. **规则持久化仅order服务**：配置Nacos数据源（flow+degrade）作为示范，生产环境按需扩展
5. **web-context-unify: false**：关闭上下文合并，支持链路限流模式的正确运行

## 四、单元测试（已通过，12个用例）

| 测试类 | 用例数 | 验证内容 |
|--------|-------|---------|
| ProductBlockHandlerTest | 4 | FlowException/DegradeException/任意BlockException/秒杀限流 |
| ProductFallbackHandlerTest | 2 | findById降级含异常信息/秒杀降级返回500 |
| ProductFeignClientFallbackFactoryTest | 3 | findByPid超时降级/不可用降级/getPort降级 |
| RequestOriginParserDefinitionTest | 3 | 正常origin/无origin/空白origin |

## 五、Sentinel全面手动测试指南

### 5.1 测试环境准备

#### 5.1.1 基础设施检查清单

启动微服务前，先确认以下基础设施正常运行：

| 组件 | 地址 | 验证方法 |
|------|------|---------|
| MySQL | localhost:3306 | `mysql -u root -p -e "SELECT 1"` |
| Nacos Server | localhost:8848 | 浏览器访问 http://localhost:8848/nacos |
| Sentinel Dashboard | localhost:8858 | 浏览器访问 http://localhost:8858 |

> **Nacos默认账号**：nacos/nacos | **Sentinel默认账号**：sentinel/sentinel

#### 5.1.2 微服务启动顺序

1. `ProductApplication` → 端口8081，等待启动完成
2. `UserApplication` → 端口8071，等待启动完成
3. `OrderApplication` → 端口8091，等待启动完成

#### 5.1.3 服务注册验证

启动后，依次访问以下URL确认服务可达：

```
http://localhost:8081/product/1        → 应返回商品JSON
http://localhost:8071/user/1           → 应返回用户JSON
http://localhost:8091/order/create?pid=1&uid=1  → 应返回订单JSON
http://localhost:8081/product/seckill/1001      → 应返回"秒杀成功"
http://localhost:8081/product/port              → 应返回端口信息
```

#### 5.1.4 Sentinel Dashboard注册确认

1. 浏览器打开 http://localhost:8858 ，输入 sentinel/sentinel 登录
2. 左侧菜单点击 **机器列表** → 应看到3个服务：service-product、service-order、service-user
3. **重要**：首次启动后需先访问各接口一次，Sentinel是懒加载机制，只有被访问过的接口才会在"簇点链路"中出现

---

### 5.2 核心功能测试点清单

| 编号 | 测试场景 | 规则类型 | 测试目标服务 | 关键验证点 |
|------|---------|---------|-------------|-----------|
| T1 | QPS流控-直接模式 | 流控规则 | service-product | 超阈值请求被限流，返回自定义429 JSON |
| T2 | QPS流控-关联模式 | 流控规则 | service-product | 关联资源超阈值时，当前接口被限流 |
| T3 | QPS流控-链路模式 | 流控规则 | service-product | 指定入口过来的流量超阈值时限流 |
| T4 | 流控效果-Warm Up | 流控规则 | service-product | 冷启动逐渐放量到最大QPS |
| T5 | 流控效果-排队等待 | 流控规则 | service-product | 请求匀速通过，超时丢弃 |
| T6 | 降级-平均响应时间 | 降级规则 | service-product | RT超阈值进入准降级→熔断→恢复 |
| T7 | 降级-异常比例 | 降级规则 | service-product | 异常比例超阈值触发熔断 |
| T8 | 降级-异常数 | 降级规则 | service-product | 异常数超阈值触发熔断 |
| T9 | 热点参数限流 | 热点规则 | service-product | 按商品ID参数限流 |
| T10 | 热点参数-参数例外项 | 热点规则 | service-product | 特定商品ID使用不同阈值 |
| T11 | 授权规则-黑名单 | 授权规则 | service-order | 指定origin被拒绝 |
| T12 | 授权规则-白名单 | 授权规则 | service-order | 仅指定origin可通过 |
| T13 | 系统保护-入口QPS | 系统规则 | service-product | 应用总QPS超限触发保护 |
| T14 | 系统保护-CPU使用率 | 系统规则 | service-product | CPU超限触发保护 |
| T15 | Feign整合Sentinel | Feign降级 | service-order | 下游服务不可用时fallbackFactory降级 |
| T16 | @SentinelResource | blockHandler | service-product | blockHandler和fallback分别触发 |
| T17 | 规则持久化 | Nacos持久化 | service-order | 重启后规则从Nacos自动加载 |

---

### 5.3 流量控制规则验证（T1-T5）

#### T1: QPS流控-直接模式

**目的**：验证最基础的QPS限流功能，超过阈值的请求被直接拒绝

**步骤**：

1. 确保已访问过 `http://localhost:8081/product/1` ，使资源在Dashboard中注册
2. 打开 Sentinel Dashboard → 选择 **service-product** → **簇点链路**
3. 找到资源 `GET:/product/{pid}` → 点击 **+流控** 按钮
4. 配置规则：
   - 资源名：`GET:/product/{pid}`
   - 阈值类型：**QPS**
   - 单机阈值：**2**
   - 流控模式：**直接**
   - 流控效果：**快速失败**
5. 点击 **新增**

**验证方法**：

方式A（浏览器）：快速连续刷新 http://localhost:8081/product/1 ，每秒超过2次

方式B（命令行并发请求）：
```powershell
for ($i=0; $i -lt 10; $i++) { curl http://localhost:8081/product/1; echo "" }
```

**预期结果**：
- 正常请求返回：`{"pid":1,"pname":"小米手机","pprice":1999.0,"stock":100}`
- 被限流请求返回：`{"code":429,"msg":"接口被限流了-FlowException"}`

**Dashboard验证**：
- 实时监控页面 → 可看到通过QPS ≈ 2，拒绝QPS > 0
- 簇点链路 → 该资源右侧显示通过/拒绝统计

**清理**：测试完毕后在流控规则列表中**删除**此规则

---

#### T2: QPS流控-关联模式

**目的**：验证当关联资源达到限流条件时，对当前接口限流（应用让步场景）

**场景**：关联接口达到限流阈值时，限制当前接口的访问

**步骤**：

1. 先访问 `http://localhost:8081/product/port` 使其注册到簇点链路
2. 在 Dashboard 找到 `GET:/product/{pid}` → 点击 **+流控**
3. 配置规则：
   - 资源名：`GET:/product/{pid}`
   - 阈值类型：**QPS**
   - 单机阈值：**2**
   - 流控模式：**关联**
   - 关联资源：`GET:/product/port`
4. 点击 **新增**

**验证方法**：

用工具对关联资源 `GET:/product/port` 施加 > 2 QPS 的压力，同时访问 `GET:/product/{pid}`：

```powershell
# 终端1：对关联资源施压
while ($true) { curl -s http://localhost:8081/product/port > $null }

# 终端2：访问被关联限流的接口
curl http://localhost:8081/product/1
```

**预期结果**：
- 关联资源（/product/port）QPS > 2时，当前接口（/product/{pid}）被限流
- 返回：`{"code":429,"msg":"接口被限流了-FlowException"}`

**清理**：删除此规则

---

#### T3: QPS流控-链路模式

**目的**：验证只限制从某个入口过来的流量（更细粒度控制）

> **前提**：`application.yml` 中 `web-context-unify: false` 已配置

**步骤**：

1. 在 Dashboard 找到 `findById` 资源 → 点击 **+流控**
2. 配置规则：
   - 资源名：`findById`
   - 阈值类型：**QPS**
   - 单机阈值：**2**
   - 流控模式：**链路**
   - 入口资源：`/product/{pid}`（从该URL入口过来的流量才计数）
3. 点击 **新增**

**验证方法**：
```powershell
for ($i=0; $i -lt 10; $i++) { curl -s http://localhost:8081/product/1; echo "" }
```

**预期结果**：超过2 QPS时，被限流的请求返回降级商品信息：
`{"pid":1,"pname":"商品服务限流/熔断降级-触发Sentinel保护","pprice":0.0,"stock":0}`

> **注意**：此处走的是 `@SentinelResource` 的 blockHandler，返回Product对象而非JSON
> 因为 `findById` 是 `@SentinelResource` 标注的资源，限流时走 ProductBlockHandler

**清理**：删除此规则

---

#### T4: 流控效果-Warm Up（预热）

**目的**：验证冷系统预热效果，初始QPS只有阈值的1/3，逐渐增长到最大值

**步骤**：

1. 在 Dashboard 找到 `GET:/product/port` → 点击 **+流控**
2. 配置规则：
   - 资源名：`GET:/product/port`
   - 阈值类型：**QPS**
   - 单机阈值：**9**
   - 流控模式：**直接**
   - 流控效果：**Warm Up**
   - 预热时长：**10**（秒）
3. 点击 **新增**

**验证方法**：

```powershell
# 立即快速请求，初始阶段只能通过 9/3=3 QPS
for ($i=0; $i -lt 30; $i++) { curl -s http://localhost:8081/product/port; echo "" }
```

**预期结果**：
- 前10秒内：被限流的请求较多（初始阈值=9÷3=3 QPS）
- 10秒后：逐渐放行到9 QPS
- Dashboard实时监控中可观察到QPS逐渐上升的曲线

**清理**：删除此规则

---

#### T5: 流控效果-排队等待

**目的**：验证请求匀速通过，适用于削峰填谷场景

**步骤**：

1. 在 Dashboard 找到 `GET:/product/port` → 点击 **+流控**
2. 配置规则：
   - 资源名：`GET:/product/port`
   - 阈值类型：**QPS**
   - 单机阈值：**2**
   - 流控模式：**直接**
   - 流控效果：**排队等待**
   - 超时时间：**5000**（ms）
3. 点击 **新增**

**验证方法**：

```powershell
# 并发5个请求，但排队等待会匀速放行（每秒2个）
Measure-Command { for ($i=0; $i -lt 5; $i++) { curl -s http://localhost:8081/product/port > $null } }
```

**预期结果**：
- 5个请求不会被瞬间拒绝，而是排队匀速通过
- 总耗时约 2-3 秒（5个请求 / 2QPS）
- 超过5秒排队的请求才会被丢弃
- Dashboard实时监控中QPS曲线平稳在2左右

**清理**：删除此规则

---

### 5.4 熔断降级策略测试（T6-T8）

#### T6: 降级-平均响应时间

**目的**：当接口平均响应时间超过阈值时触发熔断

**步骤**：

1. 在 `ProductController.findById` 方法中添加模拟延迟代码：
```java
@GetMapping("/{pid}")
@SentinelResource(value = "findById", ...)
public Product findById(@PathVariable Integer pid) {
    // === 测试用：模拟慢响应（测试完毕请删除） ===
    try { Thread.sleep(500); } catch (InterruptedException e) {}
    // === 测试用代码结束 ===
    Product product = productService.findById(pid);
    ...
}
```
2. 重启 shop-product
3. 先访问一次 `http://localhost:8081/product/1` 注册资源
4. 在 Dashboard 找到 `GET:/product/{pid}` → 点击 **+降级**
5. 配置规则：
   - 资源名：`GET:/product/{pid}`
   - 降级策略：**平均响应时间**
   - RT阈值：**200**（ms）
   - 时间窗口：**10**（秒，熔断持续时间）
6. 点击 **新增**

**验证方法**：

```powershell
# 持续请求，每次响应约500ms > 200ms阈值
for ($i=0; $i -lt 20; $i++) { curl -s http://localhost:8081/product/1; echo "" }
```

**预期结果**：
- 前5个请求：正常响应（Sentinel需要统计时间窗口内的数据）
- 之后：触发熔断，返回 `{"code":429,"msg":"接口被降级了-DegradeException"}`
- 熔断持续10秒后进入半开状态，再次尝试请求
- 如果响应时间仍 > 200ms，继续熔断；否则恢复

**清理**：删除降级规则 + 删除模拟延迟代码 + 重启服务

---

#### T7: 降级-异常比例

**目的**：当接口异常比例超过阈值时触发熔断

**步骤**：

1. 在 `ProductController.findById` 方法中添加模拟异常代码：
```java
@GetMapping("/{pid}")
@SentinelResource(value = "findById", ...)
public Product findById(@PathVariable Integer pid) {
    // === 测试用：模拟50%异常（测试完毕请删除） ===
    if (Math.random() > 0.5) {
        throw new RuntimeException("模拟业务异常-异常比例测试");
    }
    // === 测试用代码结束 ===
    Product product = productService.findById(pid);
    ...
}
```
2. 重启 shop-product
3. 先访问 `http://localhost:8081/product/1` 注册资源
4. 在 Dashboard 找到 `GET:/product/{pid}` → 点击 **+降级**
5. 配置规则：
   - 资源名：`GET:/product/{pid}`
   - 降级策略：**异常比例**
   - 比例阈值：**0.3**（30%异常率触发熔断）
   - 时间窗口：**10**（秒）
6. 点击 **新增**

**验证方法**：

```powershell
# 快速发送请求，触发异常比例统计
for ($i=0; $i -lt 30; $i++) { curl -s http://localhost:8081/product/1; echo "" }
```

**预期结果**：
- 部分请求正常返回商品JSON（正常请求）
- 部分请求返回降级商品（fallback触发）：`{"pid":1,"pname":"商品服务异常降级-触发Fallback保护，原因: 模拟业务异常-异常比例测试",...}`
- 当异常比例 > 30% 后：所有请求触发熔断 → `{"code":429,"msg":"接口被降级了-DegradeException"}`
- 10秒后恢复半开状态

> **注意区分**：fallback处理业务异常（RuntimeException），blockHandler处理熔断异常（DegradeException）

**清理**：删除降级规则 + 删除模拟异常代码 + 重启服务

---

#### T8: 降级-异常数

**目的**：当近1分钟异常数超过阈值时触发熔断

**步骤**：

1. 同T7添加模拟异常代码
2. 在 Dashboard 找到 `GET:/product/{pid}` → 点击 **+降级**
3. 配置规则：
   - 资源名：`GET:/product/{pid}`
   - 降级策略：**异常数**
   - 异常数：**3**（近1分钟3次异常触发熔断）
   - 时间窗口：**30**（秒）
4. 点击 **新增**

**验证方法**：

```powershell
# 持续请求直到累计3次异常
for ($i=0; $i -lt 20; $i++) { curl -s http://localhost:8081/product/1; echo "" }
```

**预期结果**：
- 异常数 < 3：部分正常，部分走fallback
- 异常数 >= 3：触发熔断 → 所有请求返回 `{"code":429,"msg":"接口被降级了-DegradeException"}`
- 30秒后恢复半开状态

> **注意**：异常数统计的时间窗口是1分钟，需在1分钟内累计到阈值

**清理**：删除降级规则 + 删除模拟异常代码 + 重启服务

---

### 5.5 系统保护规则检查（T13-T14）

#### T13: 系统保护-入口QPS

**目的**：从应用级别限制所有入口流量的总QPS

**步骤**：

1. 确保所有接口都已被访问过（簇点链路中有资源）
2. 在 Dashboard 选择 **service-product** → **系统规则** → **+新增系统规则**
3. 配置规则：
   - 阈值类型：**入口 QPS**
   - 单机阈值：**3**
4. 点击 **新增**

**验证方法**：

```powershell
# 同时对多个接口发请求，总QPS超过3
# 终端1
for ($i=0; $i -lt 10; $i++) { curl -s http://localhost:8081/product/1; echo "" }
# 终端2（同时执行）
for ($i=0; $i -lt 10; $i++) { curl -s http://localhost:8081/product/port; echo "" }
# 终端3（同时执行）
for ($i=0; $i -lt 10; $i++) { curl -s http://localhost:8081/product/seckill/1001; echo "" }
```

**预期结果**：
- 三个接口共享3 QPS额度，超出部分返回：`{"code":429,"msg":"系统保护规则触发-SystemBlockException"}`
- 注意：系统规则对**所有入口资源**生效，不仅仅对某个接口

**清理**：删除系统规则

---

#### T14: 系统保护-CPU使用率

**目的**：当系统CPU使用率超过阈值时自动限制入口流量

**步骤**：

1. 在 Dashboard → **系统规则** → **+新增系统规则**
2. 配置规则：
   - 阈值类型：**CPU 使用率**
   - 单机阈值：**0.5**（50%，故意设置低值便于触发）
3. 点击 **新增**

**验证方法**：

```powershell
# 正常请求应能通过
curl http://localhost:8081/product/1

# 用工具制造CPU负载（如运行死循环），让CPU > 50%
# 然后再次请求
curl http://localhost:8081/product/1
```

**预期结果**：
- CPU < 50%：请求正常通过
- CPU > 50%：请求被系统保护拦截 → `{"code":429,"msg":"系统保护规则触发-SystemBlockException"}`

> **注意**：此规则在Windows上同样生效。测试时阈值设低（0.5）容易触发，生产建议0.7-0.8

**清理**：删除系统规则

---

### 5.6 热点参数限流测试（T9-T10）

#### T9: 热点参数限流-基础

**目的**：根据方法参数值进行精确限流，适用于秒杀场景

**步骤**：

1. 先访问 `http://localhost:8081/product/seckill/1001` 使 `seckill` 资源注册到簇点链路
2. 在 Dashboard 选择 **service-product** → **热点规则** → **+新增热点规则**
3. 配置规则：
   - 资源名：**seckill**（必须是 @SentinelResource 的 value 值，不是URL）
   - 参数索引：**0**（第一个参数 productId）
   - 单机阈值：**2**
   - 统计窗口时长：**1**（秒）
4. 点击 **新增**

**验证方法**：

```powershell
# 对商品ID=1001快速请求
for ($i=0; $i -lt 10; $i++) { curl -s http://localhost:8081/product/seckill/1001; echo "" }
```

**预期结果**：
- 前2个请求：返回 `秒杀成功（商品ID:1001）`
- 后续请求：返回 `{"code":429,"msg":"抢购火爆，请稍后再试"}`

> **关键**：热点规则只对 `@SentinelResource` 标注的资源生效，资源名是注解的value值，不是URL路径

---

#### T10: 热点参数限流-参数例外项

**目的**：对特定参数值设置不同的限流阈值

**步骤**：

1. 在热点规则列表中，找到刚创建的 `seckill` 规则 → 点击 **编辑**
2. 展开 **高级选项** → **参数例外项** → 点击 **+添加**
3. 配置：
   - 参数类型：**int**
   - 参数值：**1001**
   - 限流阈值：**5**（商品ID=1001时允许5 QPS，其他ID仍是2 QPS）
4. 点击 **保存**

**验证方法**：

```powershell
# 商品ID=1001，允许5 QPS
for ($i=0; $i -lt 8; $i++) { curl -s http://localhost:8081/product/seckill/1001; echo "" }

# 商品ID=1002，只允许2 QPS
for ($i=0; $i -lt 5; $i++) { curl -s http://localhost:8081/product/seckill/1002; echo "" }
```

**预期结果**：
- ID=1001：前5个成功，后续被限流
- ID=1002：前2个成功，后续被限流

**清理**：删除热点规则

---

### 5.7 授权规则测试（T11-T12）

#### T11: 授权规则-黑名单

**目的**：拒绝指定来源的请求访问

**步骤**：

1. 先访问 `http://localhost:8091/order/create?pid=1&uid=1` 注册资源
2. 在 Dashboard 选择 **service-order** → **授权规则** → **+新增授权规则**
3. 配置规则：
   - 资源名：`GET:/order/create`
   - 流控应用：**app**
   - 授权类型：**黑名单**
4. 点击 **新增**

**验证方法**：

```powershell
# 带origin=app参数 → 应被拒绝
curl -s "http://localhost:8091/order/create?pid=1&uid=1&origin=app"

# 不带origin参数 → 应正常通过
curl -s "http://localhost:8091/order/create?pid=1&uid=1"

# 带origin=web参数 → 应正常通过（不在黑名单中）
curl -s "http://localhost:8091/order/create?pid=1&uid=1&origin=web"
```

**预期结果**：
- origin=app：`{"code":429,"msg":"订单授权规则不通过-AuthorityException"}`
- origin为空或origin=web：正常返回订单JSON

---

#### T12: 授权规则-白名单

**步骤**：

1. 编辑上述授权规则，将授权类型改为 **白名单**，流控应用改为 **web**
2. 点击 **保存**

**验证方法**：

```powershell
# origin=web → 允许通过
curl -s "http://localhost:8091/order/create?pid=1&uid=1&origin=web"

# origin=app → 被拒绝
curl -s "http://localhost:8091/order/create?pid=1&uid=1&origin=app"

# 无origin → 被拒绝
curl -s "http://localhost:8091/order/create?pid=1&uid=1"
```

**预期结果**：
- origin=web：正常通过
- 其他来源：`{"code":429,"msg":"订单授权规则不通过-AuthorityException"}`

**清理**：删除授权规则

---

### 5.8 Feign整合Sentinel测试（T15）

**目的**：验证下游服务不可用时，Feign通过FallbackFactory返回降级数据

**步骤**：

1. 确认三个服务都正常运行
2. 正常访问确认Feign调用OK：`curl http://localhost:8091/order/create?pid=1&uid=1`
3. **停止 shop-product 服务**（在IDEA中停止ProductApplication）
4. 再次访问：`curl http://localhost:8091/order/create?pid=1&uid=1`

**预期结果**：
- 服务正常时：返回完整订单JSON，包含商品信息
- 停止商品服务后：返回降级订单JSON：
  ```json
  {
    "oid": ...,
    "uid": 1,
    "username": "测试用户",
    "pid": 1,
    "pname": "商品服务降级-触发FallbackFactory保护",
    "pprice": 0.0,
    "number": 1
  }
  ```
- IDEA控制台输出日志：`商品服务调用降级, 商品ID: 1, 异常原因: ...`

**恢复**：重新启动 shop-product 服务

---

### 5.9 @SentinelResource测试（T16）

**目的**：验证blockHandler和fallback的触发条件区分

**步骤**：

1. 访问 `http://localhost:8081/product/1` 注册 `findById` 资源
2. 在 Dashboard 为 `findById` 添加流控规则：QPS阈值=1
3. 快速请求触发限流（blockHandler）：

```powershell
for ($i=0; $i -lt 5; $i++) { curl -s http://localhost:8081/product/1; echo "" }
```

**预期结果（blockHandler触发）**：
```json
{"pid":1,"pname":"商品服务限流/熔断降级-触发Sentinel保护","pprice":0.0,"stock":0}
```

4. 删除流控规则
5. 在 `ProductController.findById` 中添加模拟异常代码：
```java
if (pid == 999) { throw new RuntimeException("模拟业务异常"); }
```
6. 重启 shop-product
7. 访问异常触发路径（fallback）：

```powershell
curl -s http://localhost:8081/product/999
```

**预期结果（fallback触发）**：
```json
{"pid":999,"pname":"商品服务异常降级-触发Fallback保护，原因: 模拟业务异常","pprice":0.0,"stock":0}
```

> **关键区分**：
> - blockHandler → 处理Sentinel阻断（FlowException/DegradeException/SystemBlockException等）
> - fallback → 处理业务异常（RuntimeException等Throwable）

**清理**：删除流控规则 + 删除模拟异常代码 + 重启服务

---

### 5.10 规则持久化测试（T17）

**目的**：验证order服务的Sentinel规则持久化到Nacos，重启后自动恢复

**步骤**：

1. 访问 `http://localhost:8091/order/create?pid=1&uid=1` 注册资源
2. 在 Dashboard 为 `service-order` 添加流控规则（如 `GET:/order/create` QPS=2）
3. 在 Nacos 控制台 http://localhost:8848/nacos 检查：
   - 配置管理 → 配置列表 → Group=SENTINEL_GROUP
   - 应看到 Data ID = `service-order-flow-rules` 的配置
4. 在 IDEA 中重启 `OrderApplication`
5. 重启后再次访问 `http://localhost:8091/order/create?pid=1&uid=1`
6. 在 Dashboard 检查流控规则是否自动恢复

**预期结果**：
- Nacos中存在 `service-order-flow-rules` 配置项
- 重启后规则自动从Nacos加载，无需重新配置
- Dashboard流控规则列表中仍有之前添加的规则

> **注意**：只有order服务配置了Nacos持久化（flow + degrade），product和user服务默认内存存储

---

### 5.11 测试结果记录模板

| 编号 | 测试场景 | 规则配置 | 预期结果 | 实际结果 | 是否通过 | 备注 |
|------|---------|---------|---------|---------|---------|------|
| T1 | QPS流控-直接 | QPS=2,直接,快速失败 | 超限返回429 FlowException | | □通过 □失败 | |
| T2 | QPS流控-关联 | QPS=2,关联/product/port | 关联资源高压时当前接口限流 | | □通过 □失败 | |
| T3 | QPS流控-链路 | QPS=2,链路入口/product/{pid} | 链路入口流量超限时限流 | | □通过 □失败 | |
| T4 | Warm Up预热 | QPS=9,预热10s | 初始3QPS逐渐增长到9 | | □通过 □失败 | |
| T5 | 排队等待 | QPS=2,超时5000ms | 请求匀速通过 | | □通过 □失败 | |
| T6 | 降级-RT | RT>200ms,窗口10s | RT超阈值触发DegradeException | | □通过 □失败 | 需加延迟代码 |
| T7 | 降级-异常比例 | 比例>0.3,窗口10s | 异常比例超阈值触发熔断 | | □通过 □失败 | 需加异常代码 |
| T8 | 降级-异常数 | 异常数>3,窗口30s | 异常累计超3触发熔断 | | □通过 □失败 | 需加异常代码 |
| T9 | 热点参数限流 | 参数索引0,QPS=2 | 按商品ID限流返回429 | | □通过 □失败 | |
| T10 | 热点-参数例外 | ID=1001阈值5 | 特定ID不同阈值 | | □通过 □失败 | |
| T11 | 授权-黑名单 | origin=app,黑名单 | 指定来源被拒绝 | | □通过 □失败 | |
| T12 | 授权-白名单 | origin=web,白名单 | 仅指定来源通过 | | □通过 □失败 | |
| T13 | 系统保护-入口QPS | 入口QPS=3 | 总QPS超限触发保护 | | □通过 □失败 | |
| T14 | 系统保护-CPU | CPU>0.5 | CPU超限触发保护 | | □通过 □失败 | |
| T15 | Feign降级 | 停止商品服务 | 返回降级商品信息 | | □通过 □失败 | |
| T16 | blockHandler/fallback | 限流+业务异常 | 分别触发不同处理 | | □通过 □失败 | |
| T17 | 规则持久化 | Nacos数据源 | 重启后规则自动恢复 | | □通过 □失败 | 仅order服务 |

---

### 5.12 异常情况处理

| 异常现象 | 可能原因 | 解决方法 |
|---------|---------|---------|
| Dashboard看不到服务 | 1. 服务未启动 2. 未访问过接口 3. 通信端口被占 | 1. 确认服务运行 2. 先访问一次接口 3. 检查8719/8720/8721端口 |
| 规则配置后不生效 | 1. 资源名错误 2. 规则未保存 3. 阈值设置过高 | 1. 核对资源名与簇点链路一致 2. 重新配置 3. 降低阈值重试 |
| 热点规则不出现 | 1. 资源未用@SentinelResource标注 2. 资源名填的是URL而非value | 1. 确认注解 2. 使用注解的value值作为资源名 |
| 授权规则不生效 | 1. 未配置RequestOriginParser 2. origin参数名不匹配 | 1. 确认Bean已注册 2. 请求中使用origin参数 |
| 降级规则不触发 | 1. 时间窗口内请求数不足 2. 异常比例/数未达阈值 | 1. 增加请求频率 2. 降低阈值或增加异常概率 |
| Feign降级不触发 | 1. feign.sentinel.enabled未配置 2. 未配置fallbackFactory | 1. 检查yml配置 2. 检查@FeignClient注解 |
| 规则持久化失败 | 1. sentinel-datasource-nacos依赖缺失 2. Nacos中无配置 | 1. 检查pom.xml 2. 在Dashboard添加规则后检查Nacos配置列表 |
