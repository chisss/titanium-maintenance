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

14. ✅ **租户拒绝面：聚合层纵深防线已补（m2-908 登记 → m8-1102 实施，2026-09-14）**：
    - **原判定与其前提被推翻**：m2-908 判断「43 个命令补 `tenantId` 属**跨域契约破坏性变更**（命令经 Kafka/Feign 序列化），须先盘点存量在途消息」，故**只登记不实施**。m8-1102 实读证明该前提不成立——本域**无 `axon.axonserver` 配置**，`AxonConfig` javadoc 明示「单体应用模式下，命令总线/命令网关/事件存储均由 axon-spring-boot-starter 自动装配」⇒ 命令总线是**进程内 `SimpleCommandBus`**，命令**从不序列化、不跨进程**，不存在在途消息；给命令 record 加字段是**域内重构**，非契约变更。
    - 🔴 **可复用判据**：给命令/事件加字段前，先确认**该消息是否真的跨进程**——判据是**命令总线拓扑**（`SimpleCommandBus` vs `AxonServerCommandBus`/`KafkaCommandBus`），不是「record 长得像 DTO」。命名相似 ≠ 契约相同；把进程内命令当跨域契约，会凭空造出「在途消息兼容性」这个不存在的阻塞前提。
    - **实施内容**：① 43 个状态推进命令 record 末尾补 `String tenantId`（连同原有 7 个，50 个命令全覆盖）；② 聚合根新增私有 `requireSameTenant(String)`，**48 个处理器首条语句**加守卫，另 1 个幂等建案处理器（`CreateMaintenanceCaseCommand` 兼作创建路径）守卫置于重试分支内，**创建构造器豁免**（创建时尚无「聚合既有租户」可比对，其租户由创建事件落库，后续命令回放后即受守护）；③ 原 5 处内联校验（用 `MaintenanceValidationException` 且明示「租户不一致」，会**泄漏资源存在性**）统一收敛到 `requireSameTenant`；④ application 层 50 个派发点补租户实参。
    - **守卫语义（失败关闭）**：租户缺失、空白或不符**一律以「保全不存在」拒绝**（`MaintenanceNotFoundException`）——不区分「不存在」与「不属于本租户」，避免跨租户探测。
    - **纵深防御定位**：原防线只在应用层，且 `MaintenanceApplicationService.requireMaintenanceExists` 依赖**最终一致的读模型**（投影滞后时误拒而非放行，fail-safe 但可用性脆弱），orchestration 的 26 个派发点原本**无任何租户校验**。聚合层守卫**不依赖读模型**，且对新增派发路径（Saga、编排器直发命令）**自动生效**——只要命令经本聚合处理，归属校验就必然发生。
    - **测试**：`CrossTenantCommandGuardTest` 5 例（domain），两条互补判据——**行为判据**（错租户/空租户派发须拒绝且 `expectNoEvents()`；同租户不得误伤）+ **结构判据**（扫描聚合根源码，逐 `@CommandHandler` 断言首条语句 == `requireSameTenant(command.tenantId());`，并计数 48 首条 / 1 构造器 / 1 幂等建案 / 共 50 处理器，**防静默失效**）。新增处理器漏写守卫即红。
    - **门禁**：`mvn -B clean install` 全域 **554 例**（0 失败 0 错误 6 跳过）。
    - 跨域同批处置见 [docs/当前系统现状评估-2026-09.md](../docs/当前系统现状评估-2026-09.md) C-02（policy 29 处理器已补齐、billing 垫缴命令已补齐，本域为**最后一块**）。

15. ✅ **受理环节预检字段可执行性（m3-901，2026-09-11，缺口登记 G1）**：字段目录中 `executionSupported=false` 的字段在本域没有落地执行器（见 §2.1 三类根因），而**唯一拦截点原在「生效」环节**——`MaintenanceEffectApplicationService:517` 的 `validateExecutionCapabilities`。结果是这类字段**受理放行、生效必败**：用户在受理环节提交提案、走完审核流程，才在生效时拿到必然的失败。
    - **修复**：`MaintenanceFieldDraftApplicationService.record` 在取得实时目录证据后新增 `requireExecutableFields(proposals, catalog)`，对不可执行字段在**派发提案命令之前**即以 `MaintenanceValidationException` 拒绝（字段码 `fieldCode`，文案与生效环节逐字一致：`"字段尚未开放真实执行: " + fieldCode`）。
    - 🔴 **判据取实时目录证据 `PolicyFieldCatalogEvidence`，不改事件快照 `MaintenanceFieldCatalogSnapshot`**：后者不携带 `executionSupported`（即缺口登记 G3 未解），且为其补字段会改变 `sameAuthorityAs` 所依赖的 `fields.equals()` 语义——`Maintenance.java:1350` 用它比对「存量快照 vs 新采快照」，历史事件反序列化出的旧快照（缺字段 ⇒ `false`）将与新快照**永不相等**。故本任务刻意不走 G3 路线，取值口径与生效环节保持同源（同为实时目录），不引入第二套权威。
    - **测试**：`MaintenanceFieldDraftApplicationServiceTest` 新增 `shouldRejectFieldWithoutExecutorBeforeSendingCommand`（提案 `policy.holder.email` —— 真实目录中为 `proposal(...)`，即「可提案、无执行器」——断言抛 `MaintenanceValidationException` 且 `verifyNoInteractions(commandGateway)`，**锁死「拒绝发生在派发之前」**）。
    - 🔴 **连带修正两处过期测试夹具**：`MaintenanceFieldDraftApplicationServiceTest.fieldCatalog()` 与 `MaintenanceCaseProductionPathTest.fieldCatalog()` 均把 `policy.holder.mobile` 的 `executionSupported` 标为 `false`，而**真实目录 `PolicyFieldCatalog.java:75` 早已是 `executable(...)`**。夹具与目录不一致使「提案手机号」这条成功路径的用例实际断言的是线上不存在的场景；预检上线后二者立即转红（后者表现为 `Async not started`——校验失败走同步异常响应，HTTP 路径不再进入异步）。已将两处夹具对齐真实目录。
16. ✅ **Kafka 入站：`@EnableKafka` 已就绪但零监听器 · customer 四主题不接入（2026-09-14 m6-908 判定）**：本域 `infrastructure/config/KafkaConfig` 带 `@EnableKafka`，但**零 `@KafkaListener`**（开关空置、无入站消费）。经判定 customer 四主题（`created` / `updated` / `status-changed` / `relationship-added`）**不应接入**：本域对客户的唯一需求是**保全受理时点的同步存在性校验**（`CustomerServicePort.customerExists`，`MaintenanceApplicationService` 单点调用），无状态订阅语义；客户信息变更应经**保全批改**落到保单——而**本域正是该通道的执行方**（有案件、有字段执行器、有留痕、可审核），由客户事件静默改写合同要素属**本末倒置**（且姓名/证件是合同要素、年龄/性别参与费率）。判据见 [跨域事件目录 §六.16](../docs/技术文档/跨域事件目录-2026-09.md)，登记点见 `CustomerServicePort` 类注释。
    - 🔴 **可复用判据**：「有开关、无监听器」**不等于缺陷**——`@EnableKafka` 是**能力就绪**标志，缺监听器只说明当前无入站需求。判「入站缺失」前必须先确认**本域是否真有对应的领域能力可承接**（本域若无「客户信息同步」命令，订阅了也无处安放）。

17. ✅ **保全核保结论枚举跨域重复定义收敛（m8-1103，2026-09-14）**：`MaintenanceUnderwritingConclusion` 原在**本域 `common/enums/workflow`** 与**核保域 `domain/valueobject`** 各存一份同名枚举（后者落 `valueobject` 直接违根规约 §3.4.2），常量虽同而口径方法分散——本域版带 `BaseEnum` 四属性与 `accepted()`，核保域版带 `toUnderwritingStatus()`。
    - **修复**：按「跨域共享枚举入 metadata」上提至 `com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion`，合并两侧全部口径（四属性 + `completed()` + `accepted()` + `toUnderwritingStatus()` + `fromCode()`），本域 17 个引用点改 import、删除本地定义（核保域同步收敛，两域合计 26 个文件）。
    - 🔴 **落点为何选 `enums/underwriting` 而非 `enums/maintenance`**：该枚举的**定义方与解释方都是核保域**（`toUnderwritingStatus()` 的映射逻辑、事件 `MaintenanceUnderwritingAssessedEvent` 的生产方），本域是**消费方**（经 Feign 收 `conclusion` 字符串、`fromCode` 承接、用 `completed()`/`accepted()` 判断流程）；且 `toUnderwritingStatus()` 的目标类型 `UnderwritingEnum.UnderwritingStatus` 同在 `enums/underwriting`，同包无需跨子包 import。名字前缀 Maintenance 指的是「保全场景」，不构成归属判据。
    - **跨域兼容性**：Feign 契约传的是 `name()` 字符串，两版常量名逐字相同 ⇒ 收敛**不改变跨域传输形态**，存量无兼容问题；反之常量名即跨域契约，一经发布不可改名。
    - **测试**：metadata 新增 `MaintenanceUnderwritingConclusionTest` 7 例（跨域契约 `code == name()`、数字码唯一且不重排、未知码显式失败、三个口径方法逐项断言、常量计数防新增漏判）。
    - **门禁**：metadata 41 例 / maintenance 554 例 / underwriting 124 例，三域 `mvn -B clean install` 全绿。

18. ✅ **收口步骤 DOCUMENT/COMPLETE 落地 + 三处「终态结构性不可达」修复（m15-1805，2026-09-14）**：工作流模板末段为 `EFFECT → DOCUMENT → COMPLETE`，但两个步骤在写侧**无命令处理器**（只有枚举与模板定义），案件推进到 DOCUMENT 即卡死。
    - **实施**：① `RecordMaintenanceDocumentCommand` → 凭证事实落库（`MaintenanceDocumentEvidence`：模板/凭证号/时间/操作人），经既有通用 `last*` 列发布，**读侧不新增列**；② `CompleteMaintenanceItemCommand` → 终态收口，`requirePredecessorsTerminal` 要求同 `itemCode` 内 sequence 更小任务**全部 COMPLETED/SKIPPED**，仅 `IN_PROGRESS` 可收口、重复完成拒绝。
    - 🔴 **凭证模板编码取自本保全项冻结配置**（`MaintenanceItemControls.outputRule().voucherTemplateCode()`），**调用方不得自报**——`requireDocumentTemplate` 比对命令载荷，不符即 `MaintenanceValidationException`。
    - 🔴 **修掉三处「收口步骤结构性不可达」**（任一残留即整条链走不通）：
      1. **案件级冻结过严**：`requireWorkflowMutable` 在案件 `COMPLETED` 后拒绝一切推进，而案件恰在**生效回执**即置 `COMPLETED` ⇒ 收口步骤结构上不可达。改按**任务步骤类型**判定豁免（`MaintenanceStepType.closingPhase()`）。
      2. **生效回执不激活后继**：`handle(RecordMaintenanceCasePolicyApplicationCommand)` 原 `activateNext=false`，EFFECT 后继的 DOCUMENT 永不激活。改为 `true`。
      3. **读侧越权关闭终态**：投影原按「生效回执」自动把终态任务置 `COMPLETED` ⇒ 读模型**发明了写侧没有的状态**，在 `EFFECT→DOCUMENT→COMPLETE` 模板下**越过前驱顺序**把 COMPLETE 提前关闭。**删除该分支**，终态唯一来源回归写侧迁移事件。
    - 🔴 **可复用判据（终态唯一来源）**：读侧投影**只能搬运写侧已发生的事实**，不得依据「业务上大概该结束了」自行推断终态——推断逻辑与写侧守卫是**两套规则**，一旦写侧新增中间步骤（本例 DOCUMENT），读侧的推断即静默错误。守护方式：以「`@EventHandler` 消费事件类型集合」为**结构判据** + **计数断言**，防扫描规则静默失效。
    - **判定：DOCUMENT 不衔接 document 域**——实读确认 maintenance 与 document **零衔接**（无 pom 依赖、无 Port、无 Adapter、无跨域主题），凭证为**域内事实落库**而非触发下游单证生成，故未改《跨域事件目录-2026-09.md》，`CrossDomainEventCatalogTest` 12 例仍全绿。
    - **测试**：domain 值对象 4 例（非 DOCUMENT 步骤拒绝凭证 / 载荷不匹配拒绝 / 非 IN_PROGRESS 收口拒绝 / 重复收口拒绝）、聚合 5 例、application 4 例、query 2 例。**反向验证 3 项**：逐项把目标分支置为不可达 → 对应用例 RED → 恢复 → 复绿（探针全部清理）。
    - **门禁**：`mvn -B clean install` 全域 **568 例**（0 失败 0 错误 6 跳过）。

19. ✅ **批单出具触发端补齐：`maintenance-endorsement-issued` → document 域 `ENDORSEMENT_DOC`（m16-1902，2026-09-14）**：document 域 `DocumentType` 五值中 `ENDORSEMENT_DOC` 早已定义，却**零触发端**——「保单签发 → `POLICY_DOC`」「理赔结案 → `CLAIM_DOC`」两条链已通（m13-1602 / m14-1705），保全批单一条缺。本任务补齐，实现三域联动（metadata 主题登记 + 本域出站 + document 入站）。
    - 🔴 **与第 18 条 `DOCUMENT` 步骤的界限（勿混为一谈）**：m15-1805 的 DOCUMENT 步骤是保全案件**域内**的凭证事实留痕（`MaintenanceDocumentEvidence` 落库，不接 document 域）；本任务产出的是案件生效完成后**对外**的批改文书（跨域经 Kafka 交付 document 域渲染建档）。二者层级不同，故第 18 条「maintenance 与 document 零衔接」的判定在**该步骤范围内**仍成立，本任务新增的是**另一条通道**。
    - **出站机制定夺 = Kafka（非同步 Feign）**：判据沿用 m14-1705——单证是**可延迟的旁路产物**，不应把 document 域的可用性绑进保全生效主链；同步 Feign 会让保全生效因下游建档失败而回滚。实现：`EndorsementDocumentPort`（`domain/port/document/`，与 aggregate 平级）← `EndorsementDocumentAdapter`（`infrastructure/adapter/document/`，Kafka + `KafkaPublishSupport.awaitSent`），由 `MaintenanceEndorsementDocumentSaga`（`application/saga`）在事件处理路径搬运。
    - 🔴 **要素由写侧聚合固化，不由 Saga 取数**：`MaintenanceEndorsementIssuedEvent(id, EndorsementDocument, issuedAt)` 与案件级 `MaintenanceEffectStatusChangedEvent(APPLIED)` **同刻**发布，载荷自带被批改保单、保全项、字段前后值、生效时间。若改由 Saga 查读模型取数，要素正确性将取决于投影推进时序（最终一致），且把跨层查询依赖引入事件处理路径。与理赔域结案事件自足载荷同范式。
    - 🔴 **复用 `maintenance-kafka-group` 而非新建处理组**：该组已登记在 `titanium.axon.outbound-relay.groups`（首启位点取事件流**末端**）。新建组不在名单中，首次部署将从事件流**头部**重放——对全部历史已生效案件洪水式补发批单。<b>不得</b>改动本组归属配置。
    - 🔴 **实施中发现并修掉一处实质缺陷（批单字段明细会全滤空）**：原实现按 `MaintenanceFieldChange.appliedValue()` 非空筛选字段变更，而 `markApplied` 写入者在本域**无生产调用点**（仅值对象单测覆盖）⇒ 事件流中该字段**恒为 `null`** ⇒ 批单要素的字段明细会被**全部滤掉**，对外签发的批改凭证只剩空壳。**根因**：把「拟值」与「生效值」的权威来源搞错。**修复**：改为从 Policy 生效回执的 `MaintenancePolicyApplicationEvidence.appliedFields` 取「变更后」权威值（键 `保全项:业务对象:字段` 三元组，见 `EndorsementDocument.fieldKey`），回执未覆盖的字段回落案件拟值（如实呈现、不臆造）。要素推导逻辑同时**内聚到 `EndorsementDocument.of(...)` 值对象**，换取可直测性与可反向验证性。
    - 🔴 **可复用判据（值对象字段的「生产者缺席」检查）**：判「某字段是否有真实数据」不能只看它被赋值过——必须查**写入方法是否有生产调用点**（`grep` 全仓非测试代码）。仅有单测覆盖的 setter/工厂是**死字段**，围绕它设计的分支会在生产路径上静默走空。与 D14「读序依赖链」同属「跨模块咬合静默失效」类。
    - 🔴 **Axon `Matchers.exactSequenceOf` 是「前缀严格匹配」**：仅当实际事件数 **<** 期望数时为 false，**允许实际序列更长**。故既有测试中「期望 2 个事件、实际 3 个」会侥幸通过——新增事件（本例批单事件插在 `EffectStatusChanged` 与 `CompensationResolved` 之间）若不同批补进期望序列，断言即**静默失守**。本任务同批补强 3 处旧断言 + 新增 1 处（共 4 处断言批单事件序列）。**判据**：给聚合新增事件后，必须回扫所有 `expectEventsMatching(exactSequenceOf(...))` 断言，而非只看新增用例是否通过。
    - **测试**：本域 4 例新增——值对象 `EndorsementDocumentTest` 7 例（权威值优先 / 回执缺失回落 / 建档要素齐备 / 生效时间可空 / 无保全项 / 必填拒绝 / 键三元组）、聚合 1 例（生效回执同刻发布批单事件）、Saga 3 例（要素原样交付 / **失败必须上抛不吞** / 出站组名四方一致）、Adapter 3 例（主题与分区键 / 线格式为 POJO 而非二次编码 / **发布失败必须抛出**）。document 侧 11 例由该域补（入站 listener 5 + 编排器 6）。
    - **反向验证 2 项**：① 把 `EndorsementDocument.toEndorsementItem` 的权威取值改为直接取拟值 → `shouldPreferPolicyAppliedValueOverProposedValue` RED（`expected: <13900000000> but was: <changed>`），恢复即 GREEN；② 把聚合 `changeEffectStatus` 的 `if (next == APPLIED)` 批单发布分支置为不可达 → 聚合测试 **4 处**断言 RED（`:461` / `:492` / `:630` / `:671`），恢复即 GREEN。探针已清理并全文扫描确认。
    - **门禁**：本域 `mvn -B clean install` **582 例**（0 失败 0 错误 6 跳过，较 m15-1805 的 568 例 +14）；document 域 45 例；`CrossDomainEventCatalogTest` 12 例全绿。主题登记见 [跨域事件目录 §三 / §六.20](../docs/技术文档/跨域事件目录-2026-09.md)。

20. ✅ **投资账户转换端点补齐 + 保全 FUND_SWITCH 跨域衔接（m16-1901，2026-09-14）**：`MaintenanceType.FUND_SWITCH`（账户转换）案件在生效环节**无处可施**——保全侧无出口、投资域无入口，案件推进到生效即卡死。本任务补齐两端。
    - 🔴 **出口形态判据 = 同步 Feign（与第 19 条批单恰好相反）**：二者判别维度是**「该产物是否为案件生效的实质内容」**。批单是**旁路产物**（可延迟、失败不应回滚主链）⇒ Kafka；账户转换是**生效的实质内容**（成败决定案件能否置为已生效）⇒ 同步 Feign + fail-closed。若账户转换走 Kafka，保全侧将**无从判定成败**——要么误判成功、要么等一个永无回执的事件。
    - **三段实现**：investment 侧 `InvestmentAccountApi` 补账户转换契约（新 `SwitchUnitsDTO`）+ controller/provider 同范式实现；maintenance 侧新建 `domain/port/investment/InvestmentAccountSwitchPort`（与 aggregate 平级、按对端域分包）+ `infrastructure/client/investment/InvestmentAccountSwitchClient` + `infrastructure/adapter/investment/InvestmentAccountSwitchAdapter`。
    - 🔴 **适配器承担「保单 → 账户」解析**：案件只持有保单标识，适配器先按保单查账户列表、再对唯一命中账户执行转换（两步都不得插入对账户状态的本地假设）。
    - 🔴 **「不存在」与「失败」严格区分（D13 同规）**：查询**正常应答的空列表**才是「保单名下无投资账户」→ 返回 **null 原样上交**，由调用方决定处置；对端非成功码 / 响应缺数据 / 连接失败 / 超时 / 契约反序列化失败一律抛 `MaintenanceRemoteCallException`（失败关闭），**禁止把服务故障伪装成「账户不存在」**。调用方的处置 = **案件生效失败关闭**（失败码 `INVESTMENT_SWITCH_FAILED`，错误码 `MAINTENANCE_INVESTMENT_ACCOUNT_MISSING` 71006016），不静默跳过。
    - 🔴 **多账户歧义不静默择一**：同保单可派生多个形态账户（投连/万能/分红），转换目标是**业务事实**、无从推断，以 `MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS`（71006015）显式失败，交人工明确账户后再执行——避免「转换落到了另一个账户」这类事后无从对账的错账。**与「无账户」的语义分野**：无账户是**业务事实**（返 null 上交），多账户是**事实不足**（显式失败），二者错误码不得复用。
    - **生效环节两处结构性拒绝**：① FUND_SWITCH 与其它保全项**混合案件**拒绝（账户转换是案件级原子动作，与字段级批改语义不同）；② FUND_SWITCH 案件**不支持计划调度**（`applyScheduled` 拒绝，须人工立即执行）——均在 `requireContext` 闸口前置。
    - **测试**：application 7 例（分流不触 policy 回写 / 混合案件拒绝 / 调度拒绝 / 参数缺失 / **无账户失败关闭** / 重试载荷不一致 / 已完成回执重放）、adapter 8 例（空列表返 null / 租户与请求体透传 / 显式币种优先 / 多账户歧义 / 查询不可达 / 失效应答 / 转换拒绝 / 回执字段缺失）、investment 端点 6 例（后台与远程两入口同语义、参数校验、币种缺省、回查未命中）。
    - **反向验证 2 项**：① `isInvestmentSwitchTask` 置为 `return false` ⇒ **6 处 RED**（分流、混合案件、调度拒绝、参数缺失、无账户失败关闭、重试载荷）；② adapter 歧义分支置为不可达 ⇒ RED（`71006015` 退化为 `71006014`）。均恢复复绿，探针已全文扫描清理。
    - **判定：未新增 Kafka 主题**——出口是同步 Feign，故《跨域事件目录-2026-09.md》§二/§三/§六 不登记，`CrossDomainEventCatalogTest` 12 例仍全绿。
    - **门禁**：本域 `mvn -B clean install` **597 例**（0 失败 0 错误 6 跳过，较 m16-1902 的 582 例 +15）；investment 域 49 例。⚠️ 该总数与后续全量口径不一致（同源码下分模块实测合计 600），**请改用第 21 条的分模块基准**。

21. ✅ **列表/详情可见性对称守卫 + 例数口径校正（R8-03，2026-09-22，题面证伪）**：起因 R7-13 遗留(b)「`maintenance_id` 与 `caseId` 口径不一致，详情代理报资源不存在」。
    - **证伪逐环**：前端 `src/api/maintenance.ts:384` 用 caseId 拼路径 → BFF `MaintenanceServiceClient.getCase` 透传 → 本域 `MaintenanceCaseQueryWebMapper:85,89` 列表与详情**都是** `@Mapping(target="caseId", source="maintenanceId")` ⇒ 两侧逐字一致；库中 `maintenance_id` 是 **UUID 形态 varchar(128)**（实样 `1d2c6cec-…`）、**非雪花**，表中**无 `case_id` 列** ⇒ 原记录「雪花 id」系误判。
    - **真机双向对照**（成对同体、只换被测变量；需带齐 `X-Internal-Token` + `X-Operator-Id` + `X-Authorities`，缺任一即 401）：`1d2c6cec-…`（independent_case=1, init=1）→ **200** 且响应 `caseId` 与请求 path **逐字相同**；`02505bf7-…`（0,0）→ **404 / 71000000**。404 唯一来源是**可见性边界**（§7.11 双通道，历史案件走旧通道），且库分布 0,0=41 / 1,0=1 / 1,1=43 ⇒ 不可见行**在列表里根本不显示**，**UI 走不到「点了却 404」**。
    - 🔴 **本任务实际交付 = 补上的守卫**：`MaintenanceCaseVisibilitySymmetryTest` —— 列表 `specification` 的可见性谓词与详情派生方法名的 `True` 段必须逐项一致。**两侧各取真值再互比**（列表侧真跑 `search` 用 mock `CriteriaBuilder` 捕获 `isTrue` 所指字段；详情侧真跑 `findDetail`，从**被调用的那个方法**取方法名再解析），**不对照写死清单**——写死清单会把「两侧一致改动」判红，且证明不了出货物里确实有那两项（用户级 lessons 首条「断言里引用了内联副本」的形态）。**改 `specification` 里的可见性开关时必须同批改详情端派生方法名**。
    - **六向反向对照**（自动还原 + sha256 校验 25 个文件一致）：单侧增/删/退化四向全 RED、下限断言专项 RED、**两侧同加 `archived` ⇒ GREEN**（对照组，证明不误报、不对齐清单）。R4 首轮得 RED 系**编译失败**——该派生方法名全仓 **39 处调用点 / 25 个文件**，注入须**全仓改名**，逐点写锚反而脆弱。
    - **门禁与例数口径**：`mvn -B clean install` **600 例**（594 有效 + 6 skipped，0 失败 0 错误，8 模块全绿）。第 20 条的 597 与本轮相差 3（本任务 +1），已查证**差额系统计口径而非代码变更**：597 之后 5 个 commit（387d277 / 3b2717f / b4a6c83 / a856c61 / 554ce62）**改动测试文件数全为 0**，且 surefire 报告时间戳全部为本轮生成 ⇒ **分模块基准** application 152 / bootstrap 121 / domain 191 / infrastructure 95 / query 38 / web 3 显示 **597 + web 3 = 600**，历史统计最可能漏算 web 模块。⇒ 后续比对**用分模块基准，勿再以单个总数作基线**。

---

*本文档为保全域模块级规约，与根 [CLAUDE.md](../CLAUDE.md)、[AGENTS.md](./AGENTS.md) 配合使用。*
