# Titanium 保全域 (titanium-maintenance) - 多Agent协作指南

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **完成度**: 40%（开发中）
> **上级指南**: 继承根目录 [AGENTS.md](../AGENTS.md)，本文档聚焦保全域协作边界

---

## 一、模块定位与边界

保全域（`titanium-maintenance`，端口 **8083**）负责保单**生效后**的信息变更、保额调整、退保/停复效。其在领域依赖图中位于 Policy 之下、与 Underwriting/Billing 同层：

- **聚合根**：`Maintenance`（唯一），以 Axon 命令 + 事件溯源驱动。
- **职责边界**：只管理保全案件自身生命周期（创建→变更→算费→审核→执行），**不拥有**保单主数据与客户主数据，必须通过远程接口读取。
- **不做的事**：不直接改写保单/客户聚合；不承担保费费率计算的权威逻辑（仅记录算费结果，权威计费在计费域）。

**Agent 工作原则**：一个 Agent 专注本域；跨域改动通过事件或 Feign 契约协商，禁止越界直接修改 policy/customer 模块源码。

---

## 二、与其他域的交互点

### 2.1 同步调用（Feign，强依赖）— 出向

| 调用方文件 | 目标域 | 接口 | 用途 |
|-----------|-------|------|------|
| `client/PolicyServiceClient.getPolicyById` | 保单域 `titanium-policy` `/api/v1/policies/{id}` | GET | 校验保单存在 |
| `client/PolicyServiceClient.getPolicyStatus` | 保单域 `/api/v1/policies/{id}/status` | GET | 校验保单状态是否允许该保全类型（ACTIVE / 已失效） |
| `client/CustomerServiceClient.getCustomerById` | 客户域 `titanium-customer` `/api/v1/customers/{id}` | GET | 校验客户存在 |

> 调用均带 `X-Tenant-ID` 请求头。**注意**：本域 Web 入口拦截器用的是 `X-Tenant-Id`，Feign 出向用 `X-Tenant-ID`，大小写不一致需在契约层对齐。
> 当前 `feign.hystrix.enabled: false`，无熔断降级，保单/客户域不可用会直接导致保全创建失败。

### 2.2 异步事件（Kafka）— 出向发布

`MaintenanceEventHandler`（Axon `@EventHandler`）将以下事件发布到 Kafka，Key 为保全 ID：

- `MaintenanceCreatedEvent` → `MAINTENANCE_CREATED`
- `MaintenanceStatusChangedEvent` → `MAINTENANCE_STATUS_CHANGED`
- `MaintenanceChangeAddedEvent` → `MAINTENANCE_CHANGE_ADDED`
- `MaintenancePremiumCalculatedEvent` → `MAINTENANCE_PREMIUM_CALCULATED`
- `MaintenanceExecutedEvent` → `MAINTENANCE_EXECUTED`

下游消费者（监管域采集、计费域对账、通知域推送）应订阅上述 Topic。

### 2.3 上游消费（入向，规划中）

按根 AGENTS.md 领域事件链，保全域应消费保单域的 `PolicyActivatedEvent`（保单生效后方可发起保全）。当前代码中**尚未见到该消费者实现**，属待补能力——新增 Agent 需在 `infrastructure/messaging` 下创建对应监听器。

### 2.4 交互拓扑

```
                 getPolicyById / getPolicyStatus (Feign 同步)
  titanium-maintenance ───────────────────────────────────────▶ titanium-policy (8081?)
        │                getCustomerById (Feign 同步)
        ├────────────────────────────────────────────────────▶ titanium-customer (8081)
        │
        │  PolicyActivatedEvent (Kafka 入向, 待实现)
        ◀────────────────────────────────────────────────────  titanium-policy
        │
        │  Maintenance* 系列事件 (Kafka 出向)
        └────────────────────────────────────────────────────▶ 监管域 / 计费域 / 通知域
```

---

## 三、文件锁定建议

下列文件为高耦合/高频冲突点，多 Agent 协作时须声明独占写锁（一时刻仅一个写者）：

```yaml
core_aggregate:        # 聚合根 + 命令/事件，领域负责人优先
  - titanium-maintenance/titanium-maintenance-domain/src/main/java/com/titanium/maintenance/aggregate/Maintenance.java

cross_domain_contract: # Feign 契约，变更须通知保单/客户域 Agent
  - titanium-maintenance/titanium-maintenance-infrastructure/src/main/java/com/titanium/maintenance/client/PolicyServiceClient.java
  - titanium-maintenance/titanium-maintenance-infrastructure/src/main/java/com/titanium/maintenance/client/CustomerServiceClient.java
  - titanium-maintenance/titanium-maintenance-api/src/main/java/com/titanium/maintenance/api/client/MaintenanceApiClient.java

orchestration:         # 应用编排（薄，唯一服务类，易冲突）
  - titanium-maintenance/titanium-maintenance-application/src/main/java/com/titanium/maintenance/application/service/MaintenanceApplicationService.java

messaging:             # 事件发布
  - titanium-maintenance/titanium-maintenance-infrastructure/src/main/java/com/titanium/maintenance/messaging/MaintenanceEventHandler.java

bootstrap_config:      # 启动类 + 端口/Feign扫描配置
  - titanium-maintenance/titanium-maintenance-bootstrap/src/main/java/com/titanium/maintenance/Bootstrap.java
  - titanium-maintenance/titanium-maintenance-bootstrap/src/main/resources/application.yml
```

---

## 四、Agent 任务分工

### 4.1 角色矩阵

| 角色 | 负责文件域 | 关注点 |
|------|-----------|--------|
| **Agent-Domain** | aggregate / command / event / enums / valueobject / service | 命令校验、状态机、事件溯源一致性 |
| **Agent-App** | application/service | 跨域校验编排、Feign 调用、命令网关 |
| **Agent-Infra** | client / config / repository / projection / entity / messaging | 仓储实现、Feign 契约、Kafka 发布 |
| **Agent-Web** | controller / TenantInterceptor / WebMvcConfig | REST 入口、租户头解析 |
| **Agent-Query**（新设） | 待建 query 子模块 | 补 CQRS 读模型、Projection 视图 |
| **Agent-Test** | 各层 test | 补 domain/app/infra 单测 + 跨域集成测试 |

### 4.2 典型任务编排

**任务A：修复 Feign 启动缺陷（高优先）**
```
1. [Agent-Infra] 修正 Bootstrap.java @EnableFeignClients basePackages
                 → com.titanium.maintenance.client
2. [Agent-Infra] 在 PolicyStatusResponse 补 isTerminated() 方法
3. [Agent-Test]  启动验证 + 创建保全案件冒烟测试
```

**任务B：补齐保单生效事件消费**
```
1. [Agent-Lead]  与保单域 Agent 确认 PolicyActivatedEvent 契约
2. [Agent-Infra] infrastructure/messaging 新增监听器
3. [Agent-App]   决定生效后是否预创建/标记可保全
4. [Agent-Test]  跨域事件链集成测试
```

**任务C：补建 query 子模块（CQRS 整改）**
```
1. [Agent-Architect] 设计 Projection 视图与 QueryResult
2. [Agent-Query]     QueryHandler + 读模型实体
3. [Agent-Infra]     Projection 投影更新（监听 Maintenance* 事件）
4. [Agent-Web]       查询接口切换到 QueryGateway
```

---

## 五、协作检查清单

### 5.1 Feign 契约同步（重点）
- [ ] 修改 `PolicyServiceClient`/`CustomerServiceClient` 接口前，**先与保单域/客户域 Agent 确认对端真实接口签名**（路径、入参、响应字段）
- [ ] 对端接口变更（路径、字段、状态枚举值）须**主动通知保全域 Agent**，避免运行期反序列化失败
- [ ] 请求头大小写统一（`X-Tenant-Id` vs `X-Tenant-ID`）
- [ ] 内部 response record 字段与对端 DTO 保持一致

### 5.2 事件契约同步
- [ ] 新增/修改 Maintenance 事件字段时，通知所有下游消费 Agent（监管/计费/通知）
- [ ] Kafka Topic 常量集中在 `MaintenanceConstants.KafkaTopic`，禁止散落硬编码
- [ ] 事件保留 `tenantId`，Key 使用保全 ID 保序

### 5.3 DDD 与多租户
- [ ] 聚合根命令处理器只校验+apply 事件，状态变更仅在 `@EventSourcingHandler`
- [ ] 终态（COMPLETED/REJECTED）保护规则不被绕过
- [ ] 所有命令/事件/查询携带 `tenantId`
- [ ] domain 层不直接依赖 infrastructure / 外部域

### 5.4 交付前
- [ ] 修复第七章致命缺陷（Feign 扫描包、isTerminated）后方可联调
- [ ] 三层补充单元测试
- [ ] 补 README.md
- [ ] 跨域改动已在协作频道登记并解锁文件

---

## 六、参考

- 根协作指南：[../AGENTS.md](../AGENTS.md)
- 本域模块规约：[./CLAUDE.md](./CLAUDE.md)
- 最佳实践参考：`titanium-policy`、`titanium-customer`（90% 完成）
- 领域事件链全图见根 AGENTS.md 第四章

---

*本文档随保全域演进持续更新；新增跨域交互或缺陷整改时同步修订。*
