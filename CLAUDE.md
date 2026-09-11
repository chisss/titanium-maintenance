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

`MaintenanceCreatedEvent`、`MaintenanceStatusChangedEvent`、`MaintenanceChangeAddedEvent`、`MaintenancePremiumCalculatedEvent`、`MaintenanceExecutedEvent`。全部经 `MaintenanceKafkaEventPublisher` 转发到对应 Kafka Topic（`MaintenanceConstants.KafkaTopic.*`），读模型投影另由 query 侧 `MaintenanceProjectionEventHandler` 维护。

> 🔴 **分区键不统一，判据是「消费端的保序维度」而非「本域聚合 ID」**（见 §7.12）：
> - `MAINTENANCE_EXECUTED` → **`policyId`**（唯一消费方 policy 域按保单回写，须按保单保序）；
> - 其余四个主题 → `maintenanceId`（全仓无消费方，待出现消费方时再按同一判据评估）。

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

# 构建保全域（域目录即 Maven reactor，一次构建全部子模块）
cd /Users/sunwei/titanium-project/titanium-maintenance
mvn clean install -DskipTests

# 启动保全域服务（端口 8083）
cd titanium-maintenance-bootstrap
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
7. ~~**🟡 异常吞噬**：`validatePolicyStatusForMaintenance` 的 `catch (Exception)` 会把所有异常归并为 `PolicyNotFoundException`，易掩盖真实错误。~~ ✅ **已修复（D13）**：`PolicyServiceAdapter` 现严格区分「不可达」与「不存在」——保单域**正常应答**但业务码失败/数据缺失 → 返回 `false`/`null`（业务语义）；调用**抛异常**（连接失败、超时、5xx、契约反序列化）→ 抛 `MaintenanceRemoteCallException`（错误码 `MAINTENANCE_POLICY_REMOTE_ERROR` 71006012，Web 层映射 HTTP 502）。应用层 `validatePolicyStatusForMaintenance` 的 catch-all 已删除，技术故障不再被伪装成「保单不存在」。
8. **🟡 缺测试**：domain/application/infrastructure 三层均缺单元测试，违反根规约第九章；补测试为交付前必做项。
9. **🟡 缺 README**：本模块尚无符合规约第十二章的 README.md。
10. ✅ **单任务点命令已清理（m0-705，2026-09-11）**：删除 5 个已被案件级命令取代的命令——`InitializeMaintenanceWorkflowCommand`（回填职责并入 `CompleteMaintenanceCaseInitializationCommand`）、`RequestMaintenanceEffectCommand`/`RecordMaintenancePolicyApplicationCommand`/`FailMaintenanceEffectCommand`（案件级 `RequestMaintenanceCaseEffectCommand`/`RecordMaintenanceCasePolicyApplicationCommand`/`FailMaintenanceCaseEffectCommand` 调用同一值对象方法，功能全覆盖且强制全部生效任务原子处理，约束更紧）、`RecordMaintenanceFieldChangesCommand`（字段提案唯一入口为 `ProposeMaintenanceFieldChangesCommand`，经 `MaintenanceFieldProposalPlanner` 做目录权威校验）。同步删除 `MaintenanceConstants.KafkaTopic` 中零引用的 `POLICY_UPDATED`/`CUSTOMER_UPDATED`，并更新 `DESIGN.md` 四处叙述。
11. ✅ **双回写通道按案件代数互斥（m0-707，2026-09-11，缺陷 D9）**：保全域并存两代案件与两条回写通道——
    - **独立建案**（`t_maintenance_view.independent_case = true`）：案件平台任务级生效，经 `MaintenanceEffectApplicationService` → `PolicyMaintenanceApplicationPort` → policy `ApplyPolicyMaintenanceCommand`（字段级 + 版本 + 快照哈希）同步回写；
    - **历史案件**（`independent_case = false`）：旧版整案执行 `POST /web|api/v1/maintenances/{id}/execute`，经 `maintenance-executed` Kafka 主题 → policy `MaintenanceExecutedEventListener` → `MaintenanceWriteBackStrategy` 异步回写。

    **原先的缺口**：旧入口只校验 `status = APPROVED`，而该状态可经 `ChangeMaintenanceStatusCommand` 任意设置，独立建案因此也能进旧入口 → 同一保单被两条通道重复施加。**收敛手段**：`MaintenanceApplicationService.executeMaintenance` 在 `requireMaintenanceExists` 之后、状态校验之前拒绝 `independentCase=true` 的案件（`MaintenanceLegacyExecutionIndependentCaseForbiddenException`，错误码 `71003003`，Web 层映射 HTTP 409）。`ExecuteMaintenanceCommand` 生产侧唯一发送点即该方法，**无旁路**。
    **为何不删旧通道**：新版通道 `requireContext` 强制 `independentCase=true 且 initializationCompleted=true`（`MaintenanceViewRepository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue`），对历史案件抛 `MaintenanceNotFoundException`，**无法替代旧通道**；旧入口的 `legacy-execution-enabled` 开关默认值保持 `true` 以兼容在途旧案。
12. ✅ **保全执行事件分区键改按保单（m0-718，2026-09-11）**：`MaintenanceKafkaEventPublisher` 原对 5 个主题一律用 `maintenanceId` 作分区键，但 `MAINTENANCE_EXECUTED` 的唯一消费方是 policy 域 `MaintenanceExecutedEventListener`，它按 `message.policyId()` 回写保单状态与要素。
    **原缺陷**：同一保单的多次保全执行（退保前先改受益人、多次批改等）散落不同分区被**并行**消费，Kafka「同分区内保序」落空 → policy 侧 m0-707 建立的「字段级 version + 快照哈希」过期写保护失效，**旧版本回写可能后到并覆盖新版本**。
    **修复**：仅 `MAINTENANCE_EXECUTED` 的分区键改为 `policyId`（缺失时退化为 `maintenanceId` 并 `log.warn` 暴露数据异常，与 underwriting `underwriting-decided` 先例同构）；其余四个主题全仓无消费方，**保持按保全 ID 不动**（YAGNI，待出现消费方时再按同一判据评估）。
    **判据**：分区键的选取必须与**消费端的聚合/回写维度**一致，而非与「本域聚合 ID」想当然一致；同一业务键的事件数 >1 时，键选错即等于放弃保序。
    **测试**：新增 `MaintenanceKafkaEventPublisherTest` 3 例——① 核心：执行事件按 `policyId` 分区；② `policyId` 缺失退化为保全 ID 且不抛异常；③ 其余无消费方主题仍按保全 ID 分区（防顺手改动其语义）。

13. ✅ **回溯期间重算的复用证据读取顺序（m1-809，2026-09-11，D14 全域排查命中）**：`MaintenanceRetroactivePeriodRecalculationApplicationService#recalculate` 原先在 `sendAndWait(StartMaintenanceRetroactivePeriodRecalculationCommand)` **之后**才调 `reusableProductEvidence(...)`，而该方法读的是读模型 `t_maintenance_retroactive_period_adjustment_view`；`Start` 事件（`MaintenanceRetroactivePeriodRecalculationStartedEvent`）的投影按 `tenantId + maintenanceId` **清空**同一批期间行（`MaintenanceRetroactivePeriodRecalculationProjectionEventHandler:40`）——即「先删后读」。
    **实际影响**：当前**不触发**。`reusableProductEvidence` 仅在 `sameOperation(view, operationId)` 为真时读库，而此时 `Maintenance` 聚合的 `Start` 处理器会走 `sameRequest(operationId, requestHash)` 早返回、**不发事件**（`Maintenance.java:786-788`），投影因而不会执行清空。故这是**设计脆弱点而非在逃缺陷**——正确性依赖「应用层读序 + 聚合早返回 + 投影删除」三者跨模块咬合，任一环节演进而无人复核即静默丢失期间差异明细（`retroactivePeriodCount` 归零）。
    **修复**：把 `reusableProductEvidence(view, input, recalculationId)` 提到 `Start` 派发**之前**，与 `affectedPeriods(...)` 同属「为本次重算取证」的只读步骤；读取失败即前置取证失败，此时尚无检查点可标记失败，故直接抛出而不派发 `Fail` 命令（与 `Fail` 处理器的 `requireRetroactivePeriodRecalculation` 前置守卫一致）。
    **测试**：`MaintenanceRetroactivePeriodRecalculationApplicationServiceTest#shouldReuseProductCheckpointWhenRetryingBillingFailure` 补 `InOrder` 顺序断言——`periodAdjustmentViewRepository.findBy...` 必须先于 `commandGateway.sendAndWait(Start...)`，顺序一旦回退即红。

14. **🟠 租户拒绝面：防线在应用层、聚合层无兜底（m2-908 实读登记，2026-09-11）**：
    - **实况**：`Maintenance` 是**全域唯一聚合根，含 50 个 `@CommandHandler`**，其中**仅 5 处**内联租户校验（`Maintenance.java:1188/1234/1334/1381/1752`）。租户防线实际落在**应用层**——31 个命令派发点分布于 11 个文件，且**三种形态混用**：`findBy*AndTenantId` 租户维度读模型查询（3 文件）、`tenantId()` 与 fact/snapshot 比对（8 文件）、`requireMaintenanceExists(maintenanceId, tenantId)`（`MaintenanceApplicationService` 6 处，内部即 `findByMaintenanceIdAndTenantId`）。
    - **为何无法照搬 policy/billing 模式**：50 个命令 record 中**仅 7 个带 `tenantId` 字段**（`CreateMaintenanceCommand`/`CreateMaintenanceCaseCommand`/`StartMaintenanceItemWithdrawalCommand`/`ConfigureMaintenanceItemWithdrawalRecoveryCommand`/`ProposeMaintenanceFieldChangesCommand`/`ResolveMaintenanceFieldConflictCommand`/`RefreshMaintenanceFieldConflictsCommand`），其余 **43 个命令契约上就没有租户**——「没有租户可比」而非「忘了比」，聚合层无从校验。批量补字段属**跨域契约破坏性变更**（命令经 Kafka/Feign 序列化），须先盘点存量在途消息，故本任务**只登记不实施**。
    - **风险敞口**：当前 11 个派发文件**均已带某种租户防线**，且全域**无 `@Saga`、无 `@EventHandler` 发命令**（投影发命令已被 ArchUnit `queryShouldNotDependOnCommandGateway` 禁令覆盖），故**未失守**；但一旦新增绕过应用层的派发路径（Saga、事件直发、新的编排器直接 `sendAndWait`），聚合层无任何兜底。
    - **跟进方向**：优先给「资金类 + 状态终态类」命令补 `tenantId` 并加聚合层 `requireSameTenant`（参照 `BillingAccount.requireSameTenant` 范式：失败关闭 + 用 `*_NOT_EXIST` 码不泄漏资源是否存在）；CMD 契约变更须评估在途消息兼容性。
    - 跨域同批处置见 [docs/当前系统现状评估-2026-09.md](../docs/当前系统现状评估-2026-09.md) C-02（policy 29 处理器已补齐、billing 垫缴命令已补齐，本域为唯一遗留）。

15. ✅ **受理环节预检字段可执行性（m3-901，2026-09-11，缺口登记 G1）**：字段目录中 `executionSupported=false` 的字段在本域没有落地执行器（见 §2.1 三类根因），而**唯一拦截点原在「生效」环节**——`MaintenanceEffectApplicationService:517` 的 `validateExecutionCapabilities`。结果是这类字段**受理放行、生效必败**：用户在受理环节提交提案、走完审核流程，才在生效时拿到必然的失败。
    - **修复**：`MaintenanceFieldDraftApplicationService.record` 在取得实时目录证据后新增 `requireExecutableFields(proposals, catalog)`，对不可执行字段在**派发提案命令之前**即以 `MaintenanceValidationException` 拒绝（字段码 `fieldCode`，文案与生效环节逐字一致：`"字段尚未开放真实执行: " + fieldCode`）。
    - 🔴 **判据取实时目录证据 `PolicyFieldCatalogEvidence`，不改事件快照 `MaintenanceFieldCatalogSnapshot`**：后者不携带 `executionSupported`（即缺口登记 G3 未解），且为其补字段会改变 `sameAuthorityAs` 所依赖的 `fields.equals()` 语义——`Maintenance.java:1350` 用它比对「存量快照 vs 新采快照」，历史事件反序列化出的旧快照（缺字段 ⇒ `false`）将与新快照**永不相等**。故本任务刻意不走 G3 路线，取值口径与生效环节保持同源（同为实时目录），不引入第二套权威。
    - **测试**：`MaintenanceFieldDraftApplicationServiceTest` 新增 `shouldRejectFieldWithoutExecutorBeforeSendingCommand`（提案 `policy.holder.email` —— 真实目录中为 `proposal(...)`，即「可提案、无执行器」——断言抛 `MaintenanceValidationException` 且 `verifyNoInteractions(commandGateway)`，**锁死「拒绝发生在派发之前」**）。
    - 🔴 **连带修正两处过期测试夹具**：`MaintenanceFieldDraftApplicationServiceTest.fieldCatalog()` 与 `MaintenanceCaseProductionPathTest.fieldCatalog()` 均把 `policy.holder.mobile` 的 `executionSupported` 标为 `false`，而**真实目录 `PolicyFieldCatalog.java:75` 早已是 `executable(...)`**。夹具与目录不一致使「提案手机号」这条成功路径的用例实际断言的是线上不存在的场景；预检上线后二者立即转红（后者表现为 `Async not started`——校验失败走同步异常响应，HTTP 路径不再进入异步）。已将两处夹具对齐真实目录。
    - **判据（可复用）**：**测试夹具承载的是「目录/契约快照」，一旦生产侧目录演进，夹具不会自动跟随**——新增「依据目录权威做前置拒绝」的校验时，务必先核对夹具是否仍代表线上现状，否则会把夹具漂移误判为逻辑缺陷。

---

*本文档为保全域模块级规约，与根 [CLAUDE.md](../CLAUDE.md)、[AGENTS.md](./AGENTS.md) 配合使用。*
