# Titanium 保全域 (titanium-maintenance) - 模块开发规约

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **完成度**: 40%（开发中）
> **上级规约**: 继承根目录 [CLAUDE.md](../CLAUDE.md)，本文档仅描述保全域特有内容

---

## 一、模块概述与业务定位

保全域负责保单**生效后**的全部信息变更与价值调整，是保单全生命周期管理的关键一环。核心业务能力围绕一个保全案件（`Maintenance`）展开：

- **保单信息变更**：投保人变更、受益人变更、缴费方式变更、标的变更、投保人信息/被保人信息变更、保险期间变更等
- **保额调整**：保额变更（`COVERAGE_AMOUNT_CHANGE`）、保障责任变更、加保/减保
- **退保与停复效**：保单中止（`POLICY_SUSPENSION`）、复效（`POLICY_RESUMPTION`/`POLICY_REINSTATEMENT`）、退保终止（`POLICY_TERMINATION`）

保全案件的处理流程为：**创建案件 → 添加变更记录 → 计算保费/退费 → 审核状态流转 → 执行保全**。每个动作都以 Axon 命令驱动，并通过事件溯源重建聚合状态，最终以领域事件向 Kafka 广播，供下游域（监管、计费、通知等）消费。

保全发起前必须校验保单与客户的真实状态，因此本域与**保单域**、**客户域**存在强同步依赖（见第七章 Feign 耦合说明）。

---

## 二、技术栈与端口

继承根规约技术栈（Java 21 / Spring Boot 4.0.1 / Axon 4.10.0 / Kafka 4.0.1 / MySQL / Redis / Liquibase）。本域特有运行参数：

| 配置项 | 值 | 来源 |
|-------|----|----|
| **服务端口** | `8083` | `application.yml` server.port |
| **context-path** | `/maintenance` | `application.yml` |
| **应用名** | `titanium-maintenance` | spring.application.name |
| **数据库** | `jdbc:mysql://localhost:8066/titanium_maintenance` | datasource.url |
| **Kafka group** | `maintenance-group` | spring.kafka.consumer.group-id |
| **Axon 处理器** | `maintenance`（subscribing，threadCount=4） | axon.eventhandling.processors |
| **Axon 序列化** | jackson（general/events/messages） | axon.serializer |
| **JDK** | `/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home` | 根规约 |

> Axon 事件处理器采用 `subscribing` 模式，事件与命令在同一线程同步投影，注意与 `tracking` 模式的语义差异。

---

## 三、子模块分层结构

> **重要差异（与根规约不一致，属于已知技术债，见第七章）**：
> 1. **缺少独立 `titanium-maintenance-query` 子模块**，查询能力直接由 `application` 层调用 `domain` 领域服务实现，未做 CQRS 读写分离。
> 2. **领域层包名为 `com.titanium.maintenance` 直接下挂**（如 `com.titanium.maintenance.aggregate`、`.command`、`.event`），**非** 根规约约定的 `com.titanium.maintenance.domain.*`。
> 3. **启动类命名为 `Bootstrap.java`**，非根规约约定的 `MaintenanceApplication.java`。

```
titanium-maintenance/
├── titanium-maintenance-api/                # API层：远程接口 + DTO
│   └── api/client/MaintenanceApiClient.java
├── titanium-maintenance-domain/             # 领域层（包名 com.titanium.maintenance.*）
│   ├── aggregate/Maintenance.java           # 聚合根（唯一）
│   ├── command/                             # 5个命令（非 record，含 builder/of 工厂）
│   ├── event/                               # 5个领域事件
│   ├── enums/                               # MaintenanceStatus / MaintenanceType
│   ├── valueobject/                         # CustomerId/PolicyId/MaintenanceId/
│   │                                        #   MaintenanceAmount/MaintenanceChange
│   ├── repository/MaintenanceExclusionRepository.java  # 互斥配置端口（参考数据；写侧 MaintenanceRepository 已删）
│   └── service/SurrenderRefundDomainService  # 退保退费领域服务（纯计算，无仓储）
├── titanium-maintenance-infrastructure/     # 基础设施层
│   ├── client/                              # ★Feign：PolicyServiceClient/CustomerServiceClient
│   ├── config/                              # AxonConfig / KafkaConfig
│   ├── event/MaintenanceKafkaEventPublisher # Axon EventHandler → Kafka 发布
│   ├── entity/MaintenanceExclusionDO         # 仅保留互斥配置 DO（参考数据，JPA CRUD）
│   └── repository/                          # MaintenanceExclusionRepositoryImpl + jpa/（仅互斥配置）
├── titanium-maintenance-application/        # 应用层（薄）
│   └── application/service/MaintenanceApplicationService.java  # 唯一服务类
├── titanium-maintenance-web/                # Web层
│   ├── controller/MaintenanceController
│   ├── TenantInterceptor / WebMvcConfig     # 租户头拦截
├── titanium-maintenance-common/             # 通用层：constant/exception/util
└── titanium-maintenance-bootstrap/          # 启动模块
    ├── com/titanium/maintenance/Bootstrap.java
    └── resources/application.yml            # port 8083
```

---

## 四、核心领域模型

### 4.1 聚合根 Maintenance

位置：`titanium-maintenance-domain/.../aggregate/Maintenance.java`。标注 `@Aggregate`，以 `MaintenanceId`（`@AggregateIdentifier`）为标识，采用事件溯源：每个命令处理器只校验业务规则并 `apply` 事件，状态由 `@EventSourcingHandler` 重建。

聚合持有字段：`policyId / customerId / maintenanceType / status / effectiveTimeType / specificEffectiveDate / totalAmount / refundAmount / description / changes(List<MaintenanceChange>) / 审计字段 / tenantId`。

> 🔴 **写侧持久化选型（2026-07-09 收敛，见根 §4.1 与《持久化选型规范》）**：`Maintenance` 为**纯事件溯源**聚合（保全变更需历史 + 驱动 billing/payment 跨域），写模型状态只在 Axon 事件流（`AxonConfig` 装配 `EventSourcingRepository`）。
> - 已删除残留写侧 JPA：`MaintenanceCaseEntity`/`MaintenanceChangeRecordEntity`/`MaintenanceEffectiveTimeEntity`、对应 `*JpaRepository`、`MaintenanceRepositoryImpl`、领域写端口 `MaintenanceRepository`、只读转发的领域服务 `MaintenanceService(Impl)`。原写表 `t_maintenance_case` 从未被写入（`save` 为死代码），旧读路径实为读空表，本次一并修复。
> - **存在性/在途/DTO 读取统一改走 CQRS 读模型** `MaintenanceViewRepository`（表 `t_maintenance_view`，最终一致），由 `MaintenanceApplicationService` 直接注入；聚合状态不再回退 JPA。
> - **保全类型互斥**为参考/配置数据（非聚合写状态）：保留为 `MaintenanceExclusionDO`（`*DO`，禁用 `Entity` 后缀）+ JPA CRUD，经领域端口 `MaintenanceExclusionRepository` 访问。
> - 读模型投影保留 `MaintenanceView`；Kafka 发布器为 `MaintenanceKafkaEventPublisher`。

### 4.2 命令处理与业务规则（真实代码）

| 命令 | 处理器行为 | 业务规则 |
|------|-----------|---------|
| `CreateMaintenanceCommand` | 构造器 `@CommandHandler` → `MaintenanceCreatedEvent` | 初始状态置为 `PENDING`，金额归零 |
| `ChangeMaintenanceStatusCommand` | → `MaintenanceStatusChangedEvent` | `COMPLETED`/`REJECTED` 为终态不可再变更；新旧状态相同时拒绝 |
| `AddMaintenanceChangeCommand` | → `MaintenanceChangeAddedEvent` | 向 `changes` 追加一条变更记录 |
| `CalculateMaintenancePremiumCommand` | → `MaintenancePremiumCalculatedEvent` | 写入 `totalAmount` 与 `refundAmount`（退保/减保退费） |
| `ExecuteMaintenanceCommand` | → `MaintenanceExecutedEvent` | 执行后状态置为 `COMPLETED` |

> 命令类当前**未使用 record**（含 `builder()` 与 `of()` 工厂方法），与根规约「命令必须用 record」不符，属待整改项。

### 4.3 事件（5个）

`MaintenanceCreatedEvent`、`MaintenanceStatusChangedEvent`、`MaintenanceChangeAddedEvent`、`MaintenancePremiumCalculatedEvent`、`MaintenanceExecutedEvent`。全部经 `MaintenanceKafkaEventPublisher` 转发到对应 Kafka Topic（`MaintenanceConstants.KafkaTopic.*`），消息 Key 为保全 ID；读模型投影另由 query 侧 `MaintenanceProjectionEventHandler` 维护。

### 4.4 枚举

- **MaintenanceStatus**：`PENDING → PROCESSING → APPROVED → COMPLETED`，旁支 `REJECTED`。`COMPLETED`/`REJECTED` 为终态。
- **MaintenanceType**（16种）：投保人变更、受益人变更、缴费方式变更、增额/减额缴费、中止、复效、终止，以及新增的保单信息/期间/保额/被保人信息变更、复效、标的变更、吸烟状态变更、保障责任变更。

### 4.5 状态流转校验（应用层）

`executeMaintenance` 要求保全状态为 `APPROVED` 方可执行；`findPendingMaintenancesByPolicyId` 将 `PENDING/PROCESSING/APPROVED` 视为在途；创建时按保全类型校验保单状态：复效要求保单已失效，多数变更要求保单 `ACTIVE`。

---

## 五、编码规约

完全继承根规约第四章。本域特别强调：

- **多租户**：所有命令/事件携带 `tenantId`；Web 层经 `TenantInterceptor` 解析 `X-Tenant-Id`（注意 Feign 调用下游用的请求头为 `X-Tenant-ID`，大小写需统一）。
- **日志**：SLF4J `{}` 占位符（`MaintenanceKafkaEventPublisher` 已遵循）。
- **依赖注入**：构造器注入（现有服务类均已构造注入，符合规约）。
- **跨域调用**：仅允许在 `application` 层通过 Feign client 调用，`domain` 层禁止直接依赖外部域。
- **整改方向**：新增命令/查询时应使用 record，并逐步将查询拆入独立 query 子模块。

---

## 六、构建与运行

```bash
export JAVA_HOME=/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home

# 仅构建本模块（依赖已装入本地仓库时）
cd /Users/sunwei/titanium-project
mvn -pl titanium-maintenance -am clean install -DskipTests

# 启动保全域服务（端口 8083）
cd titanium-maintenance/titanium-maintenance-bootstrap
mvn spring-boot:run
```

前置依赖：MySQL（8066 端口库 `titanium_maintenance`）、Redis（6379）、Kafka（9092），以及**保单域(8081 待确认实际端口)与客户域(8081)需可达**，否则创建保全案件的 Feign 校验会失败。

---

## 七、已知缺陷与待办

> 以下基于当前真实代码核对，优先级从高到低。

1. **🔴 Feign 客户端扫描包路径错误（致命）**：`Bootstrap.java` 声明 `@EnableFeignClients(basePackages = "com.titanium.maintenance.infrastructure.feign")`，但实际 Feign 接口位于 `com.titanium.maintenance.client`。该包下无任何 client，启动时 `PolicyServiceClient`/`CustomerServiceClient` 不会被注册为 Bean，`MaintenanceApplicationService` 构造注入将失败。修复：改为 `basePackages = "com.titanium.maintenance.client"`。
2. **🔴 编译错误（疑似）**：`MaintenanceApplicationService.validatePolicyStatusForMaintenance` 调用 `policyStatus.isTerminated()`，但 `PolicyServiceClient.PolicyStatusResponse` 仅定义了 `isActive()`，缺少 `isTerminated()`。需在 `PolicyStatusResponse` 补充 `isTerminated()` 方法。
3. ~~**🟠 缺独立 query 子模块**~~ ✅ **已解决**：`titanium-maintenance-query` 已建（QueryHandler + QueryResult + `MaintenanceProjectionEventHandler` + `MaintenanceView`）。2026-07-09 写侧收敛后，应用层存在性/在途校验与读取统一走读模型 `MaintenanceViewRepository`，不再重建聚合。
4. **🟠 端口/库端口需复核**：服务端口 `8083`；数据库连到 `localhost:8066`（疑似 MyCat/中间件端口而非 MySQL 默认 3306），与其它域端口规划需统一，避免与已有域冲突。
5. **🟠 包结构不符规约**：领域层包名缺 `.domain` 段；命令未用 record；启动类名为 `Bootstrap`。需评估重构成本与向后兼容。
6. **🟡 跨域同步耦合**：创建保全强依赖保单域/客户域 Feign 同步调用，且未配置熔断（`feign.hystrix.enabled: false`），下游不可用时保全创建直接失败，无降级。
7. **🟡 异常吞噬**：`validatePolicyStatusForMaintenance` 的 `catch (Exception)` 会把所有异常归并为 `PolicyNotFoundException`（仅显式放行两类），易掩盖真实错误。
8. **🟡 缺测试**：domain/application/infrastructure 三层均缺单元测试，违反根规约第九章；补测试为交付前必做项。
9. **🟡 缺 README**：本模块尚无符合规约第十二章的 README.md。

---

*本文档为保全域模块级规约，与根 [CLAUDE.md](../CLAUDE.md)、[AGENTS.md](./AGENTS.md) 配合使用。*
