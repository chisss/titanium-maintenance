# Titanium Maintenance 设计文档

## 目标与边界

保全域拥有保全案件生命周期和跨域处理检查点，不拥有保单、客户、价格或资金事实。Application 层负责“取数、调用领域行为、调用外部 Port、记录检查点”，Domain 层保持对基础设施和远程 Port 实现无感。

非目标：

- 不在 Maintenance 维护费率表、定价公式或费用构成定义。
- 不接受调用方直接提交差额金额作为权威结果。
- 不将 Billing `POSTED` 解释为已收款或已退款。
- 不直接执行退款、佣金补付或佣金回拨。

## 分层结构

```text
Web/API
  |  DTO 映射、校验、租户头
Application
  |  MaintenancePremiumSettlementOrchestrator
  |  MaintenancePremiumSettlementApplicationService
  |  CommandGateway / Query repository / Domain Port
Domain
  |  Maintenance aggregate
  |  Product / Underwriting / Billing / Payment Ports
Infrastructure              Query
  |  Feign adapters           |  MaintenanceView
  |  external clients         |  projection/query handler
```

Port 接口定义在 `domain/port`，Feign client 和 Adapter 位于 Infrastructure。领域服务不得依赖 Port；跨域调用只由 Application 编排器完成。

### Policy 字段目录防腐层

`PolicyFieldCatalogPort` 位于 Maintenance Domain，返回本地不可变证据；`PolicyFieldCatalogClient` 和 `PolicyFieldCatalogAdapter` 位于 Infrastructure。Adapter 校验租户、产品/保单类型、业务日期回显、目录版本、SHA-256 格式、字段唯一性和敏感掩码配置，任何不完整或冲突响应均失败关闭。

共享对象类型、字段类型、敏感级别和掩码策略定义在 Metadata。Maintenance 不依赖 Policy Domain，也不保存 Policy 的 JSON Path、数据库列或命令映射。

## D2-A 保费差额流程

### 正常流程

1. 按 `maintenanceId + tenantId` 读取案件，跨租户请求直接拒绝。
2. 使用幂等键 `maintenanceId:replacement` 调 Product 创建 `MAINTENANCE` 替代计算。
3. 校验 Product 回显的业务号、产品、版本、币种、用途和结果 hash。
4. 使用幂等键 `maintenanceId:adjustment` 创建生命周期差额。
5. 记录原计算、替代计算、差额 ID/hash、方向、金额和币种。
6. 若方向为 `NONE`，记录 `NOT_REQUIRED` 并结束。
7. 若方向为 `DEBIT/CREDIT`，以 Product 差额事实调用 Billing 入账。
8. 校验 Billing 的 adjustment、hash、方向、金额、币种和状态后，记录 posting 并进入 `POSTED`。

### 故障恢复

| 已有检查点 | 重试行为 |
|---|---|
| 无 | 从 Product 替代计算开始 |
| 已有 replacement | 复用替代计算，继续生成/读取差额 |
| 已有 adjustment | 不重复调用 Product，继续 Billing |
| 已有 posting | 返回现有事实，不重复入账 |

Product 以请求 ID 幂等，Billing 以 `tenantId + adjustmentId` 幂等。Maintenance 事件检查点保证响应丢失或中途失败后可以恢复。

## 聚合约束

- 新费用流程开始后，旧人工算费入口不得覆盖权威金额。
- 价格影响型保全只有在 `POSTED` 或 `NOT_REQUIRED` 后才能执行。
- 相同 posting 可重放；冲突的 posting、原计算或 hash 必须拒绝。
- 所有命令与事件包含 `tenantId`，读模型查询必须同时按业务 ID 和租户过滤。

## 保全项与变更证据基座

### 增量兼容模型

`Maintenance` 暂时保留单值 `maintenanceType`，兼容旧创建、费用和 Policy 回写事件；同时新增 `itemInstances` 承载多项保全。旧 `MaintenanceCreatedEvent` 重放时初始化空项目集合，不修改任何历史事件字段。

新命令与事件：

| 类型 | 作用 |
|---|---|
| `AddMaintenanceItemCommand` / `MaintenanceItemAddedEvent` | 将已校验的保全项配置版本冻结到案件 |
| `RecordMaintenanceFieldChangesCommand` / `MaintenanceFieldChangesRecordedEvent` | 保存某保全项的完整字段提案 |
| `ProposeMaintenanceFieldChangesCommand` / `MaintenanceProposedSnapshotRecordedEvent` | 使用当前 Policy 和字段目录权威证据生成拟变更快照 |

项目和字段提案只允许在 `PENDING/PROCESSING` 阶段修改。聚合拒绝重复项目、配置互斥项目、非白名单字段、禁止清空字段和重复业务对象字段。

### M3-01 独立建案幂等

独立入口继续创建 `Maintenance`，不新增第二个案件聚合。`MaintenanceCaseIdempotencyKey` 以 `tenantId + MaintenanceChannel + clientRequestKey` 为范围，使用带长度前缀的 SHA-256 生成不暴露原请求键的稳定 `MaintenanceId`。请求键在边界去除首尾空白并限制为 128 字符；人工后台与 API 使用不同来源空间。

`CreateMaintenanceCaseCommand` 使用 Axon `CREATE_IF_MISSING`：首次处理时先追加结构不变的 `MaintenanceCreatedEvent`，再追加 `MaintenanceCaseOpenedEvent` 保存来源、请求键和版本化请求指纹。已有聚合收到重试时不产生新事件；聚合同时核对租户、来源、请求键和指纹，异载荷复用同键时失败。请求指纹使用 `maintenance-case-create:v1` 算法标识和长度前缀编码，覆盖首次创建的业务字段，避免简单分隔符拼接产生歧义。

Application 创建服务直接返回命令中的稳定案件 ID。`/api/v1/maintenance/cases` 与
`/web/v1/maintenance/cases` 已分别固定 API/MANUAL 来源，来源、租户和操作人不能由请求体自报。

### M3-02A Policy 建案快照

`PolicyMaintenanceSnapshotPort` 位于 Domain，返回租户、保单/客户、产品/计划版本、保单状态、单调基准版本、业务有效时点、快照引用和结构化字段值。Application 必须先调用该 Port，校验租户与保单回显及 `EFFECTIVE` 状态，再构造领域命令；客户标识只从 Policy 快照派生。

首次建案按顺序追加 `MaintenanceCreatedEvent`、`MaintenanceCaseOpenedEvent` 和 `MaintenancePolicySnapshotCapturedEvent`。第三个事件冻结完整不可变证据，不修改前两个历史事件；仅有 M3-01 两个事件的案件可在同键同载荷重试时补录第三个事件。已有快照的重试必须逐值一致，否则聚合拒绝替换基准。

Policy 已提供正式建案快照 API，Infrastructure 使用真实 Feign Adapter 校验产品/计划版本、
业务基准版本、快照引用、摘要和结构化字段；不得调用后台详情接口拼装伪权威数据。

### M3-03 Product Offering 适用性

Product 持有版本化 `ProductMaintenanceOffering`，按产品版本、PricingPlan 版本、保单状态、受理渠道和业务时点
返回允许保全项及 Offering 版本/hash 证据。Maintenance 真实 Adapter 校验回显与证据，在发送创建命令前要求
兼容主保全类型同时存在于 Offering 和业务时点有效的已发布 Maintenance 配置，且配置开放当前渠道。
任何权威端不可用、版本不匹配、不适用或配置缺失均失败关闭。M3-04 再把 Offering 与完整配置证据冻结到案件项目实例。

### M3-04 多保全项选择与初始化门禁

HTTP 建案请求优先使用有序 `itemCodes`，单案允许 1 至 10 项；为保持旧调用兼容，仍接受单个
`maintenanceType`，但两种输入必须且只能提供一种。`MaintenanceItemCode` 集中完成规范化和旧类型映射，
其中 `SURRENDER`、`POLICY_SURRENDER` 均映射为 `POLICY_TERMINATION`，首项继续填充旧
`maintenanceType` 字段供历史流程读取。

Application 在发送任何命令前解析一次 Product Offering，并按 Policy 快照业务时点解析全部项目的已发布
Maintenance 配置。项目必须同时满足 Offering 适用、编码一致、业务时点有效和渠道开放；组合必须通过
`atomicOnly` 与双向互斥校验。Policy 的 `businessEffectiveAt` 表示租户业务本地时点及其 offset，Offering
保留完整 offset 传递，Maintenance 配置有效期按同一租户的本地墙上时间比较，禁止跨租户或按 JVM 默认时区换算。

成功命令序列固定为：

```text
CreateMaintenanceCaseCommand
  -> MaintenanceCaseItemsPlannedEvent
  -> AddMaintenanceItemCommand × N
  -> CompleteMaintenanceCaseInitializationCommand
  -> MaintenanceCaseInitializationCompletedEvent
```

每个 `MaintenanceItemInstance` 冻结完整定义控制、配置 ID/版本/hash、Offering ID/版本/hash 和解析时间。
相同项目、定义及证据的重试不追加事件，任何差异均视为冲突。历史 `MaintenanceItemAddedEvent` 不含控制字段
和选择证据时，控制字段恢复为 Phase 1 默认值，选择证据恢复为明确的 legacy 证据，不伪造配置 ID 或摘要。

Axon 多命令调用不具备单事件流之外的物理事务，因此初始化完成事件是后续操作的权威门禁。未完成初始化的案件
可使用同一幂等键从缺失项目继续，但不得进入字段录入；M3-06 投影及旧兼容查询均不返回为可操作案件。

### M3-05 动态字段录入与 proposed 快照

`PUT /api/v1/maintenance/cases/{caseId}/items/{itemCode}/changes` 与后台对应路由只接收结构化字段集合，不接收任意 JSON 对象合并。Application 重新读取 Policy 当前权威快照，并按案件基准业务日期获取正式字段目录；聚合核对租户、保单、基准版本、项目白名单、目录类型、读取/提案/清空能力、停用日期及无条件必填字段。

`MaintenanceFieldProposalPlanner` 是无 Port、仓储或容器依赖的纯领域服务。它按以下顺序生成事实：

```text
before snapshot + current Policy snapshot
  -> base/current/proposed 逐字段差异
  -> 与本案其他项目草稿合并
  -> 完整 proposed snapshot + SHA-256 + axon-event 引用
  -> MaintenanceFieldChangesRecordedEvent
  -> MaintenanceProposedSnapshotRecordedEvent
```

标量字段以 Policy ID 作为差异对象标识，快照键仍为 `fieldCode`。集合字段要求调用方提交稳定 `objectId`，差异对象键和快照键统一为 `objectId:fieldCode`，禁止使用数组下标。两个项目修改同一对象字段时失败关闭；当前值已偏离 before 且不等于 proposed 时记录 `DETECTED`，后续未解决前不得生效。

每个项目冻结实际使用的目录版本、摘要、字段类型、对象类型、能力、敏感级别、掩码策略和变更类别。完整 proposed 结构随 Axon 事件保存；在接入外部快照对象存储前，引用采用 `axon-event://maintenance/...`。同一逐字段差异、完整 proposed 值及目录权威事实重试不追加事件。独立案件禁止调用旧 `RecordMaintenanceFieldChangesCommand` 绕过权威校验，历史案件与旧事件保持可重放。

### M3-06 案件查询投影

查询侧采用案件主表、项目表、字段差异表和快照表四类读模型。投影分别消费建案、Policy 快照、项目计划、项目冻结、初始化完成、字段差异和 proposed 快照事件；项目记录冻结的 Maintenance 配置与 Product Offering 版本/hash，字段记录保存 `base/current/proposed/applied` 四值及冲突状态，快照只保存类型、引用、摘要和基准版本。

独立案件以 `MaintenanceCaseInitializationCompletedEvent` 作为操作可见门禁，历史案件保持可见。新案件列表、详情以及旧 `/web/v1/maintenances` 查询、统计、在途检查统一遵守 `!independentCase || initializationCompleted`，避免半成品进入操作后台或影响业务判断。列表查询强制租户和独立案件条件，支持案件、保单、客户、项目、来源、状态、操作人、时间范围及过滤后分页计数。

`tenantId + source + clientRequestKey` 唯一索引提供并发首次创建后的胜出案件定位。Application 只在 Axon 并发冲突后读取该投影，并严格校验稳定案件 ID 与请求指纹；相同载荷继续缺失初始化步骤，异载荷继续拒绝。详情字段默认按投影冻结的目录敏感级别和掩码策略脱敏，目录元数据缺失时失败关闭为 `***`，仅认证主体持有 `maintenance:sensitive:view` 时显示原值。

### M3-07 独立资源协议

人工与 API 共用同一独立资源模型，来源分别由 `/web/v1/maintenance/cases` 和 `/api/v1/maintenance/cases` 路由固定；请求体不能覆盖来源、租户或操作人。Controller 只执行受信上下文读取、JSR-303 校验和 DTO 转换，建案、查询、字段草稿均委托 Application 门面。

字段草稿协议固定为 `caseId + itemCode + proposals`。客户端不再提交 `policyId`，且 DTO 显式拒绝未声明字段。`MaintenanceFieldDraftApplicationService` 先通过 Query Repository 的严格定位条件读取初始化完成的独立案件，再用案件投影中的 Policy 标识调用正式快照 Port；找不到案件时不触发任何跨域调用，Policy 回显仍需与租户和服务端解析的保单一致。聚合最后再核对冻结 before 快照，因此查询投影和事件流共同防止跨租户、跨保单及半成品写入。

### M3-08 集成验收与兼容切换

旧 Web 与 Feign 建案入口收敛到 `MaintenanceApplicationService.createMaintenanceCase`，该 Application 边界注入
`MaintenanceLegacyCreationFeaturePort`。属性适配器读取 `titanium.maintenance.legacy-creation-enabled`，默认开启；
关闭时在任何远程调用、读模型校验和命令发送前失败，且不影响旧查询、状态、执行或结算能力。

投影重建继续先消费结构不变的 `MaintenanceCreatedEvent`。只有该历史事件的案件保持
`independentCase=false` 并可见；新序列追加 `MaintenanceCaseOpenedEvent` 后隐藏，直到
`MaintenanceCaseInitializationCompletedEvent` 才恢复操作可见。Policy 不反向依赖 Maintenance，只从本域批改投影的
`sourceMaintenanceId` 派生已生效案件引用；在途案件查询和全部保全操作仍专属于保全管理页面。

### M4-01 流程任务实例化与详情投影

`MaintenanceWorkflowPlanner` 是无 Port、Repository 或 Spring 依赖的纯领域规划器。它只读取案件内已冻结的
`MaintenanceItemInstance`，按项目选择顺序和步骤序号生成不可变任务；任务 ID 固定为
`caseId:itemCode:stepType`。`CREATE` 初始为 `COMPLETED`，配置跳过步骤为 `SKIPPED`，每个项目首个未跳过的
必需步骤为 `READY`，首个条件步骤为 `WAITING_CONDITION`，其他可执行任务为 `PENDING`。

新案件在 `MaintenanceCaseInitializationCompletedEvent` 后追加 `MaintenanceWorkflowInitializedEvent`，不修改
Phase 1-3 已发布事件。`InitializeMaintenanceWorkflowCommand` 为已完成项目冻结但没有工作流事件的案件提供显式、
幂等回填；已有相同任务集合时不追加事件，任何差异失败关闭。只有旧事件的案件可继续重放并返回空任务列表，
禁止查询侧用当前配置伪造历史任务。

查询侧以 `t_maintenance_workflow_task_view` 保存 `tenantId + caseId + itemCode + stepType` 唯一任务投影。
事件处理器按租户写入，详情查询同样以租户和案件定位并按项目、步骤排序。Application 脱敏字段值时原样保留
任务证据，Web 详情公开任务标识、模式、条件规则和状态。M4-01 只建立可查询事实，领取、推进、条件决策和失败恢复
属于 M4-02。

### 配置职责

`MaintenanceItemDefinition` 负责保全项内部一致性，包含渠道、字段、步骤、费用、生效和互斥规则。`configuration.control` 下的 `MaintenanceItemControls` 进一步组合渠道自动审核、材料、跨字段规则、审批策略、费用公式与门禁、权限及输出模板引用。它们不判断具体产品是否开放，也不读取 Policy 字段目录；这些跨域判断必须由后续 Application 编排通过 Domain Port 完成。

案件选择项目后形成不可变 `MaintenanceItemInstance`，冻结定义、配置与 Offering 证据。发布新配置不能改变在途案件的含义。

### 配置版本聚合

`MaintenanceItemConfiguration` 是 Repository 持久化的状态式聚合，不使用 Axon。业务键为 `tenantId + itemCode + version`，生命周期为：

```text
DRAFT -> PENDING_APPROVAL -> APPROVED -> PUBLISHED -> RETIRED
             |                  |
             +-> DRAFT          +-> DRAFT
```

- 草稿可替换内容；送审后内容冻结，审批人与本轮提交人必须分离。
- 驳回返回草稿并保留理由；已发布内容不能原地修改。
- 已发布或已退役版本只能复制为具有新配置 ID 和新版本号的修订草稿。
- 发布版本按配置有效期供新案件解析；退役不改变在途案件快照。
- 生命周期操作写入不可变追加审计列表，由 Infrastructure 与当前快照在同一事务追加到审计表。
- 仅草稿允许物理删除；删除前追加 `DRAFT_DELETED` 最终审计，删除后仍可按配置 ID 查询历史审计。

发布时使用固定字段顺序的规范化 JSON 生成 SHA-256。字段规则、渠道、材料、互斥项、规则引用、权限码和通知模板等无序内容先稳定排序；哈希包含定义与配置有效期，不包含租户、配置 ID、状态和审计字段。因此等价业务内容在不同租户、操作者或审计时间下具有相同内容证据。

M2-04 的 `MaintenanceConfigurationValidator` 编排 Policy 字段目录和 `MaintenanceConfigurationReferencePort`。送审、审批和发布均运行外部校验；审批与发布重新读取最新目录。校验覆盖字段存在性、可读/可提案/可清空能力、期望类型、停用日期、敏感字段查看权限，以及规则、权限和模板引用。`executionSupported` 在 Phase 5 案件执行时校验，不阻止配置发布。引用提供端不可用或证据非权威时失败关闭。

`MaintenanceConfigurationManagementApplicationService` 在 Application 层完成“按租户读取 → 校验行版本 → 读取权威证据 → 调用聚合 → 保存”的编排。发布额外检查同租户、同保全项的已发布有效期重叠，并冻结目录版本、目录哈希和校验时间。

Web 不直接调用编排器，而通过 `MaintenanceConfigurationCommandService` 和 `MaintenanceConfigurationQueryService` 两个 CQRS 入站门面。配置属于状态式主数据，读侧复用 Domain Repository 的租户条件分页，不为同一主数据另建 Axon 投影。

`MaintenanceConfigurationFeaturePort` 将灰度判断保持在应用编排边界，Infrastructure 通过部署属性 `titanium.maintenance.configuration.write-enabled` 实现。关闭时阻止创建草稿、创建修订版和首次发布；幂等读取、详情、预览、审计和按业务时点解析不受影响。`GET /api/v1/maintenance/configurations/effective` 只返回当前租户在指定业务时点唯一有效的已发布配置。

### 配置持久化与审计

`JpaMaintenanceItemConfigurationRepository` 实现 Domain Repository Port。`t_maintenance_item_configuration` 保存当前完整 JSON 快照和 `@Version rowVersion`，业务唯一键为 `tenant_id + item_code + configuration_version`；`t_maintenance_item_configuration_audit` 只追加操作序号、前后 JSON/hash、操作者、来源 IP、关联号、结果和时间。配置保存或审计追加任一失败时事务整体回滚。

JSON Mapper 只负责聚合快照与领域对象往返，Domain 不感知 Jackson、JPA 或数据库 JSON 能力。唯一键冲突映射为 409；应用层或 JPA 检出的过期行版本统一映射为 412。

管理端审计查询读取追加表中的前后 JSON，经 Mapper 恢复为领域快照，再在 Web 层对脱敏 VO 做结构化差异计算。响应包含前后对象和 JSON Pointer 字段路径，但不返回 `before_json`、`after_json` 原文。

规则、权限和模板目前没有统一权威只读 API。Infrastructure 的默认 Adapter 返回非权威证据，确保送审、审批和发布失败关闭；后续真实 Adapter 注册后替换该默认实现。

M2-07 使用真实应用编排和内存 Repository 完成“创建草稿 → 校验 → 送审 → 审批 → 发布 → 业务时点解析”的生命周期验收，并使用 H2 MySQL 模式执行本阶段 Liquibase 更新与回滚，断言仅删除配置主表和审计表、旧案件表保持不变。

### 字段证据

`MaintenanceFieldChange` 保存 `base/current/proposed/applied` 四值。`MaintenanceFieldValue` 使用字段类型和规范化值稳定序列化，禁止事件反序列化为不确定的 `Object`：

- 数字、布尔、日期和带偏移时间在构造时解析并规范化；
- 对象和数组通过结构化 JSON 解析并生成稳定文本；
- 删除使用对应类型的空值，不以空字符串代替；
- 当前值偏离基准且不等于拟值时进入 `DETECTED`，解决后才能记录生效值。

大快照使用 `MaintenanceSnapshotReference` 保存存储键、SHA-256、Policy 版本和带偏移采集时间；`MaintenanceSnapshotSet` 强制先有 before，再有 proposed，最后才能记录 applied。

## 数据模型

`t_maintenance_view` 保存以下费用检查点：

| 字段组 | 内容 |
|---|---|
| Product 计算 | `original_calculation_id`、`replacement_calculation_id` |
| 差额事实 | `premium_adjustment_id`、`premium_adjustment_result_hash` |
| Billing 事实 | `billing_posting_id` |
| 余额结果 | `balance_direction`、`balance_amount`、`balance_currency` |
| 状态 | `premium_settlement_status` |

数据库变更由 `maintenance_premium_settlement_v2d2_202608201700_weisun_ddl.sql` 管理。

`t_maintenance_workflow_task_view` 保存 M4-01 工作流任务投影，业务唯一键为
`tenant_id + case_id + item_code + step_type`；表由
`maintenance_workflow_task_m4_01_202608251020_weisun_ddl.sql` 管理，回滚只删除该任务表。

M4-02 通过 `maintenance_workflow_transition_m4_02_202608251130_weisun_ddl.sql` 增加领取人、领取时间、重试数、
失败信息、条件判定证据和最后操作证据列。回滚只移除 M4-02 新列，保留 M4-01 任务表及既有任务数据。

M4-03 通过 `maintenance_workflow_review_m4_03_202608251315_weisun_ddl.sql` 增加审核方式、结论、策略、上下文摘要、
七门禁 JSON、意见和决定人时间列。回滚只移除审核列，保留 M4-01/M4-02 任务及操作证据。

M4-04 通过 `maintenance_workflow_underwriting_m4_04_202608251520_weisun_ddl.sql` 增加核保案件号、请求摘要、
规则/模型版本、结论、附加条件、脱敏摘要和完成时间列。回滚只移除核保列，保留既有任务与审核证据。

M4-05 通过 `maintenance_workflow_premium_quote_m4_05_202608251900_weisun_ddl.sql` 增加报价状态/ID/版本、请求摘要、
原/替代计算及结果摘要、计划版本/hash、报价结果 hash、脱敏摘要、方向、金额、币种和有效期列。回滚只移除报价列，
保留 M4-01 至 M4-04 的任务、操作、审核和核保证据。

M4-06 通过 `maintenance_workflow_settlement_m4_06_202608252140_weisun_ddl.sql` 增加 Billing posting 与 Payment 资金
证据列。两组证据独立保存，回滚只移除结算列，保留 M4-01 至 M4-05 的任务与报价证据。

M5-01 通过 `maintenance_effect_evidence_m5_01_202608251500_weisun_ddl.sql` 为案件增加正交生效状态，并为任务增加
Policy 请求、批单、实际版本、应用摘要、实际字段值和 applied 快照证据列。旧案件回填 `NOT_STARTED`；回滚逆序移除
M5-01 新列，保留案件主表、流程任务表及 Phase 4 全部检查点。

## M4-02 任务推进与条件决策

### 状态转换

| 操作 | 前置状态 | 结果状态 | 约束 |
|---|---|---|---|
| `CLAIM` | `READY` | `READY` | 记录领取人和时间，不隐式开始 |
| `START` | `READY` | `IN_PROGRESS` | 仅当前领取人可开始 |
| `COMPLETE` | `IN_PROGRESS` | `COMPLETED` | 仅 `DATA_ENTRY/VALIDATION`；校验任务必须有版本化证据 |
| `FAIL` | `IN_PROGRESS` | `FAILED` | 仅当前领取人操作，保存失败编码和原因 |
| `RETRY` | `FAILED` | `READY` | 清除领取和失败信息，累计重试次数 |
| `DECIDE_CONDITION` | `WAITING_CONDITION` | `READY/SKIPPED` | 规则版本、输入 SHA-256、结论和原因必须完整一致 |

`COMPLETE` 或条件 `SKIP` 形成终态后，仅激活同一保全项按 `sequence` 排列的下一项 `PENDING` 任务；不同项目
独立推进。`REVIEW`、`UNDERWRITING`、`FEE_SETTLEMENT` 和 `EFFECT` 必须由后续专用命令完成，通用完成命令
不能绕过审核、核保、资金或生效门禁。

### 幂等与审计

每次任务操作生成规范化载荷 SHA-256，并在聚合内以 `operationId -> payloadHash` 保存检查点：同操作号同载荷
直接幂等返回，同操作号异载荷失败关闭。`MaintenanceWorkflowTaskTransitionedEvent` 同时保存当前任务的
`beforeTask/afterTask`，以及存在时被激活后继任务的 `activatedTaskBefore/activatedTaskAfter`，投影据此原子更新两项
任务视图。案件详情结构化返回 `assignment`、`retryCount`、`failure`、`conditionEvidence` 和 `lastOperation`，
操作人员无需从状态字符串或日志反推变更前后事实。

M4-01 的 `MaintenanceWorkflowInitializedEvent` 仍可重放：旧任务 JSON 缺少 M4-02 字段时恢复为未领取、零重试且
无失败、条件或最后操作证据。已发布初始化事件本身不增加字段。

### 信任与租户边界

Application 在发送命令前，使用 `tenantId + caseId + taskId` 校验初始化完成的独立案件及任务；跨租户请求与不存在
统一处理。人工和系统路由共用聚合状态机，但人工路由不能完成 `VALIDATION`，条件决策也只开放系统 API。

当前系统 API 是受信证据提交边界：Maintenance 校验证据形态、一致性和哈希格式，但尚未主动调用 Rule Engine
验证规则版本及输入摘要。生产自动决策前必须定义 Maintenance Domain 的规则取证 Port，在 Infrastructure 实现固定
服务地址的真实 Adapter，并由 Application 编排“取证、校验回显、发命令”；路由还必须使用服务身份认证和细粒度
授权。证据端不可用、不权威或回显不一致时失败关闭，不能退化为调用方自报结论。

## M4-03 人工审核与 API 自动审核

审核策略在 Application 解析。Application 通过案件项目投影中的 `configurationId + version + contentHash` 回读
Maintenance 自有不可变配置，逐项勾稽后取得审批策略；禁止按当前业务时点重新解析有效配置。Domain 只接收
`MaintenanceWorkflowReviewEvidence`，并再次校验审核模式、结论、策略版本和门禁证据的一致性。

人工审核仅从 `/web` 路由进入。Application 强制建案人与审核人分离；聚合强制任务已经领取、已经开始且决定人是
当前领取人。人工可通过或拒绝，不携带自动门禁证据。拒绝时先追加完整任务前后值事件，再追加案件审核拒绝事件，
任务和案件均进入 `REJECTED` 终态，后续任务不激活。

自动审核仅从 `/api` 路由进入，只允许 `APPROVE`。渠道、产品、保全项三类证据从冻结案件事实派生，身份、材料、
金额和风险四类证据由受信 API 提交 SHA-256 摘要。七类门禁全部通过且任务未被人工领取时才发送审核命令；任一
门禁失败返回 `MANUAL_REQUIRED`，不写任务转换或拒绝事件。自动证据摘要排除服务端决定时间，相同操作号同载荷
重试稳定幂等，异载荷失败关闭。

## M4-04 Underwriting 风险结论

Maintenance Domain 定义 `MaintenanceUnderwritingPort`，Application 从租户隔离的案件、冻结项目和字段差异投影
编排请求，Infrastructure 的 `MaintenanceUnderwritingAdapter` 调用 Underwriting 正式 API。Domain Service 不依赖
Port；调用方不能提交核保结论、规则版本或模型版本。存在未解决字段冲突时，Application 在远程调用前失败关闭。

外部请求冻结保单基准版本、产品/计划版本、配置版本/内容摘要、结构化风险字段差异、项目和幂等键。Adapter 逐项
校验租户、案件、保单版本、项目、幂等键和载荷摘要回显。远程异常不发送领域命令，因此同一外部幂等键可重试；
同键异载荷由 Underwriting 和 Maintenance 双侧拒绝。

聚合将 `APPROVED/CONDITIONAL_APPROVED` 记为任务完成并激活后继，将 `REJECTED` 记为任务和案件终态，
将 `MANUAL_REVIEW` 记为 `WAITING_EXTERNAL`。`NOT_REQUIRED` 仅能写入配置为 `SKIPPED` 且零风险差异的任务。
任务事件和查询投影只保存核保案件号、请求摘要、版本、结论、附加条件、脱敏摘要和完成时间，不保存原始风险值。

## M4-05 Product 版本化报价

Maintenance Domain 定义 `ProductMaintenancePremiumQuotePort`，Application 从租户隔离案件、冻结项目、before/proposed
快照和任务投影组装报价请求，Infrastructure Adapter 调用 Product 正式 API。调用方只提供定价输入和当前仍缺失于
Policy 快照的 `originalCalculationId`，不能提交金额、方向、报价版本、摘要或有效期；HTTP DTO 拒绝全部未声明字段。

Product 复算完整 `payloadHash`，严格绑定产品版本和期望 PricingPlan 版本，复用 `MAINTENANCE` 确认计算与生命周期
差额事实形成报价。`quoteVersion` 固定等于差额事实 `resultHash`，报价有效期为创建后 24 小时。Adapter 逐字段校验
租户、案件、保单基准版本、产品/计划版本、快照 hash、项目、幂等键、载荷摘要和结果证据，不一致或过期均失败关闭。

Product 持久化幂等字段最长 64 字符，因此 Maintenance 将跨域幂等键定义为
`SHA-256(maintenanceId + ":" + taskId + ":" + operationId)`。相同操作号同载荷由聚合幂等；新操作号可重报价并将当前
任务投影替换为新证据，旧事件仍保留。成功报价允许任务从 `READY/IN_PROGRESS/QUOTED` 进入或保持 `QUOTED`，不会
完成费用任务，也不会激活 `EFFECT`。`feeMode=NONE` 和 `OPTIONAL + SKIP` 记录 `NOT_REQUIRED`，不调用 Product、
Billing 或 Payment。

## M4-06 Billing 与 Payment 门禁

`MaintenancePremiumSettlementApplicationService` 只接受操作号、支付方式和原因。Application 从案件与任务投影读取
当前有效报价，通过 `BillingPremiumLifecyclePort` 以报价差额事实幂等入账，再按方向通过 Payment Port 创建或查询
确定性收款单、或读取 Billing 创建的独立退款单。调用方不能覆盖金额、方向、币种、posting 或资金结果。

Billing posting 与 Payment 资金结果是两个独立检查点。聚合只有收到 `POSTED + SUCCEEDED/NOT_REQUIRED` 才完成
`FEE_SETTLEMENT` 并激活同项目 `EFFECT`；`PENDING` 进入 `WAITING_EXTERNAL`，Payment 失败或 posting 冲正进入
`FAILED`。受控重试复用已经成功的 posting，保留历史资金事件，并在再次调用时重新勾稽外部状态。`NONE + 0`
记录无差额 posting 和无需资金证据，不生成费用行且不调用 Payment。

Adapter 对租户、案件、任务、保单、报价 hash、posting、方向、金额、币种和确定性支付单号逐字段勾稽。远端事实
缺失、冲突或不属于当前案件时失败关闭。任务投影分别公开结构化 `billingPostingEvidence` 与
`fundSettlementEvidence`，后台不需要从日志或单一状态推断会计和资金进度。

## M4-07 集成验收与兼容切换

集成验收覆盖数据录入、条件执行、API 自动审核、Underwriting、Product 报价、Billing/Payment 成功和 `EFFECT`
激活的完整命令序列，并以两个保全项交错推进证明任务只激活本项目后继。投影重建同时覆盖新工作流全部检查点和
仅含历史 `MaintenanceCreatedEvent` 的旧案件，未修改任何已发布事件结构。

旧后台人工金额入口在 Application 边界注入 `MaintenanceLegacyPremiumCalculationFeaturePort`，Infrastructure 属性
Adapter 读取 `titanium.maintenance.legacy-premium-calculation-enabled`，默认开启。关闭时在读案件、调用外部依赖或
发送命令前失败，只影响 `POST /web/v1/maintenances/{id}/calculate-premium`；任务级权威结算、旧无金额权威结算和
查询均不经过该开关。

## M5-01 生效状态与 Policy 回执证据基座

案件以独立 `MaintenanceEffectStatus` 保存生效进度，不再从 `MaintenanceStatus` 或任务状态临时推断。`EFFECT` 任务只有
在 `READY` 时才能冻结 `MaintenanceEffectRequestEvidence` 并进入 `WAITING_EXTERNAL`；请求证据绑定请求 ID、SHA-256、
期望 Policy 版本、生效模式/时点及 proposed 快照摘要。Policy 失败保留原请求证据并进入可恢复 `FAILED`。

成功回执必须匹配请求 ID 和期望版本，实际版本必须递增，applied 快照版本必须等于实际版本，并包含结构化实际字段值。
任务收到回执后才完成；全部必需 `EFFECT` 任务完成或跳过后，案件生效状态进入 `APPLIED`，兼容案件状态才转为
`COMPLETED`。该状态变化由追加事件重放，不在命令处理器中直接赋值。

案件与任务投影分别公开生效状态和完整回执证据，同时将 Policy 实际字段值回填到四值视图、将 applied 快照写入三快照
视图。Application 对回执中的实际字段值复用字段目录敏感级别和掩码策略；没有目录描述时失败关闭为 `***`。M5-01
不调用 Policy 写 API，也不开放生产生效 HTTP，正式跨域编排留在 M5-02。

## M5-02 Policy 正式应用与立即生效

`MaintenanceEffectApplicationService` 是立即生效唯一应用编排器。它只读取初始化完成的独立案件、目标 `EFFECT`
任务、字段差异和 proposed 快照，要求案件为 `IMMEDIATE`、恰有一个非跳过 `EFFECT` 任务、没有字段冲突，并实时
校验全部字段 `executionSupported=true`。当前首批只开放 `policy.holder.mobile`。

编排严格分为三个可重放事实：先发送 `RequestMaintenanceEffectCommand` 冻结稳定请求 ID、载荷 SHA-256、期望 Policy
版本和 proposed 快照摘要；再通过 Domain 定义的 `PolicyMaintenanceApplicationPort` 调用 Infrastructure Feign Adapter；
最后发送 `RecordMaintenancePolicyApplicationCommand` 保存 Policy 权威回执。远端异常发送
`FailMaintenanceEffectCommand`，不生成伪批单或伪 applied 值。

请求 ID 由租户、案件和任务稳定派生，完整载荷摘要由 Maintenance 与 Policy 按相同规范化算法独立计算。Policy 回执
必须勾稽请求 ID、期望/实际版本、应用摘要、applied 快照版本和结构化实际字段。响应丢失后，重试复用冻结请求事实并
由 Policy 返回原批单；同请求 ID 异载荷失败。人工和 API 分别通过
`POST /web|api/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect` 进入相同编排，调用方不能提交 Policy 实际结果。

## M5-03 案件级原子生效、状态交易与补偿

`MaintenanceEffectApplicationService` 现在加载案件全部非跳过 `EFFECT` 任务。所有任务必须同时处于 `READY` 或
`WAITING_EXTERNAL`，并共享案件级稳定请求 ID、期望 Policy 版本、生效时点和 proposed 快照摘要。Application 只调用
Policy 一次，再分别发送案件级请求、回执或失败命令；聚合命令覆盖任务集合必须与案件完整集合一致，旧单任务命令在
多项目案件失败关闭，因此不会出现一个项目已完成而其他项目失败的持久化状态。

状态类项目通过 Metadata 的 `MaintenanceType.policyMaintenanceAction()` 映射到 `SUSPEND`、`RESUME`、`REINSTATE` 或
`TERMINATE`。字段型项目仍按字段目录执行；状态项目允许无字段值，但 Policy 回执必须包含 `stateAction`、
`statusBefore` 和 `statusAfter`。`SURRENDER/POLICY_SURRENDER` 使用退保终止原因，普通终止使用解除合同原因。

若 Policy 已返回成功事实，而回执校验或 Maintenance 命令写入失败，Application 不再误记为 Policy 调用失败，而是发送
`RecordMaintenanceEffectCompensationCommand`。独立补偿事件保存请求、批单、实际版本、应用摘要和失败原因，案件详情
返回 `effectCompensation`。操作员继续使用既有人工生效路由重试；Policy 按原请求幂等返回同一批单，案件回执成功后
追加补偿解决事件。当前未配置项目级独立生效能力，生产路径一律整案原子。

## M5-04 未来生效计划与可靠调度

未来计划是案件聚合事实，不是单个 `EFFECT` 任务事实。`MaintenanceEffectSchedule` 冻结生效类型、租户 `ZoneId`、UTC
下一执行时间、计划状态、尝试次数和最后错误；创建、暂停、恢复、尝试、失败、完成均使用追加事件。租约只用于节点间
运行时协调，由 `MaintenanceEffectScheduleLeasePort` 定义、JDBC Adapter 通过数据库条件更新实现，不进入领域事件。

`FUTURE/SPECIFIED_DATE` 使用案件指定时点，`NEXT_BILLING_DATE/POLICY_ANNIVERSARY` 使用 Policy 正式快照提供的未来日期。
建案初始化完成后自动建计划，历史案件可通过独立 HTTP 幂等补建。调度扫描和已到期计划的人工立即执行取得同一租约后，Application
重新读取 Policy 版本、任务状态、Billing posting 及 Payment 收退款事实，再调用 M5-03 案件级正式生效链；不复用到期
前的外部事实判断。未入账且已过期报价失败关闭，已入账报价允许越过自然过期但必须重新向 Billing 勾稽。

计划到期早于审核、核保或收费完成时属于可重试等待，按配置退避，不记为永久失败。尝试事实尚未写入时不伪造失败事实；
Policy 回执已经使案件进入 `APPLIED` 后，任何调度失败都不能将生效状态回退。若计划关闭命令失败，后续租约只从任务
投影恢复原权威回执并幂等关闭计划，不再次调用 Policy。聚合同时拒绝 `APPLIED` 向其他生效状态转换，并接受同执行标识
的重复关闭命令。

## M5-05A 追溯建案边界与 Product 定价时点

追溯建案首先对全部冻结保全项执行 `MaintenanceEffectiveRule`。所选模式必须存在于每个项目的 `allowedModes`；
`RETROACTIVE` 指定时点必须早于租户当前业务时间、不早于 Policy 权威保单起期，并按冻结租户 `ZoneId` 的自然日校验
`maxRetroactiveDays`。自然日边界避免“允许第 30 日”因时分秒差异被误拒。未来和指定日使用同一规则对象校验方向与
`maxFutureDays`，非日期模式拒绝调用方夹带指定日期。

Product 报价的业务时点由案件时态决定：追溯案件使用冻结的 `specificEffectiveDate`，其他案件保持使用保单业务起期。
Maintenance 只负责选择正确时点并保存报价检查点，价格计算仍由 Product 拥有。M5-05A 不创建影响分析成功事实，也不
新增事件或投影；M5-05D 在完整影响、期间重算与关闭期间处理证据形成后才允许正式追溯生效。

## M5-05B 追溯影响分析证据与后台清单

`POST /api|web/v1/maintenance/cases/{caseId}/retroactive-impact-analysis` 只接受稳定 `operationId`。Controller 依赖
`MaintenanceCaseCommandService` 应用层写门面，由门面委托
`MaintenanceRetroactiveImpactAnalysisApplicationService`；Web 不直接依赖 orchestration。编排从租户隔离的案件投影
取得保单、追溯时点和分析范围，随后按固定顺序调用 Policy、Billing、Payment、Claim 四个
`MaintenanceRetroactiveImpactSourcePort`。四个 Port 必须全部注册且全部成功，任一缺失、远端失败或契约回显不一致都
形成分析失败事实，不允许操作员自报影响对象、金额、状态或处理结论。

每个权威 Adapter 使用固定服务地址和正式 API，校验请求对应的保单回显；Policy 额外校验租户回显。各域返回稳定对象
标识、业务单号、发生时间、权威状态、证据版本和证据摘要，Maintenance 只做归一化、稳定排序和全域结果 hash，不复制
各域业务判断。Claim 当前契约没有币种，因此理赔影响项不推断金额和币种。跨域读取不是分布式快照，分析版本、范围、
单项证据 hash 和全域结果 hash 用于识别和审计该次观察；M5-05C 执行期间重算前仍需按权威状态重校验。

聚合使用开始、完成、失败三个追加事件保存分析 ID、版本、操作号、请求摘要、范围、覆盖域、计数和错误。相同操作号与
相同请求可重放已完成结果，异载荷冲突；终态重放不再次调用四域。案件主投影只保存分析摘要，影响项逐行保存到独立
`t_maintenance_retroactive_impact_view`，以租户、案件、分析和稳定影响项 ID 唯一约束，避免不可筛选、不可索引的 JSON
清单。案件详情返回分析状态、覆盖域、阻断/待处理计数，以及按严重度、归属域和处理状态组织的结构化影响项。

`COMPLETED` 的语义仅为四域权威证据收集完整；即使存在 `blocksEffect=true` 或待处理项，分析仍可完成并如实展示，绝不
等价于“允许生效”。M5-05D 要求阻断/待处理计数均为零，并继续勾稽分析、期间重算、逐期间和 Billing 权威处理结论。

## M5-05C Product/Billing 追溯期间重算

`POST /api|web/v1/maintenance/cases/{caseId}/retroactive-period-recalculation` 只接受稳定 `operationId`。Controller 继续
只依赖 `MaintenanceCaseCommandService`，由 `MaintenanceRetroactivePeriodRecalculationApplicationService` 读取案件、
已完成影响分析版本、Billing 结构化影响项和费用计算检查点。调用方不能提交期间、金额、计算 ID、分析版本或会计期间
状态。Product 与 Billing 均通过 Domain 定义的 Port 及 Infrastructure Adapter 调用正式 API，Application 负责
“取数 → 发命令冻结请求 → 调 Port → 发命令保存检查点”的跨域编排，Domain Service 不依赖 Port。

Product 以每个 Billing 影响项的历史账单金额作为 before，以不可变替代确认计算的 `installmentAmount` 作为 after，
返回 `PERIOD_V1`、原/替代计算结果摘要、总方向/金额以及逐期间结果 hash。Product 不复制 Billing 会计事实，Maintenance
也不复制精算模型；无账单影响时返回 `NONE/0` 和空期间列表，仍可用输入/结果摘要完成勾稽。

Billing 服务端把 `periodStart` 映射为 `YearMonth` 并读取 `AccountingPeriodStatusPort`。零差额进入 `NOT_REQUIRED`，
开放期间进入 `POSTED`，关闭期间进入 `CLOSED_PERIOD_REVIEW`；只要存在关闭期间，批次就是 `REVIEW_REQUIRED`。关闭
期间只写不可变调整行和人工处理事实，不更新原账单或历史会计期间。默认 Adapter 从
`titanium.billing.accounting-period.closed-through` 读取关闭线，未配置时期间开放。

Maintenance 聚合分别追加开始、Product 已记录、完成和失败事件。状态为 `RECALCULATING → PRODUCT_COMPLETED →
COMPLETED/REVIEW_REQUIRED`；任一阶段可进入 `FAILED`。Billing 失败保留 Product 检查点，同一操作号重试复用 Product
结果，只补 Billing；外部成功但本地写入失败时稳定请求号保证 Product/Billing 幂等重放。新操作号递增案件级重算版本，
不会覆盖历史事件。

案件主投影保存分析绑定、Product/Billing 摘要和失败信息；逐期间 before/after、差额、会计期间及 Billing 状态保存到
`t_maintenance_retroactive_period_adjustment_view`。Product 事件即可建立明细，Billing 失败不会删除；完成事件补齐
会计期间与处理结果。`REVIEW_REQUIRED` 是可审计重算终态但不是 Policy 生效许可，必须先完成 M5-05D 关闭期间处理。

## M5-05D 关闭期间处理与 Policy 追溯生效

`POST /api|web/v1/maintenance/cases/{caseId}/retroactive-period-resolution` 只接受稳定 `operationId`、目标开放会计期间和
原因。租户与操作人来自请求头，Billing 批次、源摘要、差额和处理凭证全部由案件投影与 Billing 权威事实派生。Application
按“冻结处理请求 → Billing 正式 API → 保存权威结论”编排，案件追加状态机为 `RESOLVING → COMPLETED/FAILED`。
Billing 调用/事实校验失败才写失败事件；本地完成命令超时直接向调用方暴露并允许同操作号重试，避免把已成功的远端
结转误记为失败。

Billing 不改写关闭期间原账单，而是把每条差额结转至目标开放期间，服务端生成 posting reference。Maintenance 主投影
保存请求/结果摘要、目标期间、处理行数和失败事实，逐期间投影补齐处理状态、目标期间、posting reference 与行结果
hash。处理结论要求期间 ID 唯一、所有明细目标期间一致，并映射到当前批次的每条 `CLOSED_PERIOD_REVIEW` 行。

`MaintenanceEffectApplicationService` 对追溯案件执行完整资格校验：影响分析完成且无阻断/待处理项；分析与期间重算
ID、版本、hash 一致；Product/Billing 检查点及逐期间数量/hash 完整；存在关闭期间时处理结论必须完成。随后通过 Billing
正式 GET API 再次读取权威结论并逐行勾稽，才构造 Policy `retroactiveEvidence`。Policy 将证据纳入请求 hash，只在证据
完整时接受 `RETROACTIVE`；应用事件后追加 `PolicyMaintenanceRetroactiveEvidenceRecordedEvent`，权威回执尾部原样回显
证据，Maintenance 对回显不一致继续失败关闭。既有字段与状态应用事件结构不变。

## M5-06A 顺序外字段冲突与显式解决

Application 在生效调用前读取 Policy 最新快照。版本漂移时，聚合按 `objectId + fieldCode` 重新计算 current 值，保存
冲突刷新事件并重建案件级 proposed 快照。存在未解决冲突时案件进入 `CONFLICTED`，事件先于跨域调用落地，Policy
不会收到含糊请求；没有冲突的版本漂移直接更新期望 Policy 版本并继续正式生效。

字段级解决动作限定为 `USE_CURRENT`、`USE_PROPOSED` 和 `REENTER`。聚合保存动作、理由、操作者、时间及解决前后证据，
同一 `operationId + requestHash` 幂等，异载荷冲突。已解决字段只有在 Policy current 再次变化时才重新冲突；全部解决后
按案件时态恢复 `NOT_STARTED/SCHEDULED`。采用 current 产生的无变化字段从 Policy 请求中过滤，非状态交易不允许提交
空字段交易。`WAITING_EXTERNAL` 使用已冻结请求，不因重复调用刷新版本。

人工与 API 通过独立 `/maintenance/cases/{caseId}/field-conflicts/refresh|resolve` 路由操作，Web 只依赖
`MaintenanceCaseCommandService`。请求 DTO 拒绝未知字段，案件详情和独立冲突投影提供结构化 before/current/proposed、
解决结论和审计信息；敏感值仍由字段目录和授权决定是否脱敏。自动刷新操作号通过固定长度阶段 hash 派生，避免外部
128 字符操作号与版本后缀组合后突破持久化列长度。

`WITHDRAW_ITEM` 不属于字段值选择，必须和财务补偿作为独立状态机处理。

## M5-06B 项目撤销与财务补偿

项目撤销以 `itemCode + operationId + requestHash` 冻结请求，同操作同载荷幂等，异载荷或第二个撤销请求冲突。首批只允许
多项目案件撤销；案件生效状态为 `EFFECTING/APPLIED` 或目标项目已有 Policy 成功回执时失败关闭，操作员必须新建反向
保全，不能删除已生效项目。聚合保留原 `MaintenanceItemInstance`，撤销是追加事实而非物理删除。

Application 在撤销开始事件成功后才调用外部系统。没有 Billing posting 时记录 `NOT_REQUIRED`；存在 posting 时通过
`BillingPremiumLifecyclePort.reverse` 建立一对一不可变冲正，严格校验租户、案件保单/客户、原 posting/result hash、
相反方向、金额和币种。原资金成功时，原 `DEBIT` 通过 Payment 独立退款单退回原收款，原 `CREDIT` 通过确定性收款单
追回原退款；原资金处理中不先冲正，记录 `WAITING_FUNDS` 并在重试时回查；原资金失败或已反转时只执行 Billing 冲正。

原资金最终状态进入补偿证据，聚合据此强制“成功必须执行逆向资金、未成功只能无需资金”，防止应用层或内部调用方跳过
Payment 门禁。Billing 成功但 Payment 异常时记录含冲正证据的 `FAILED` 补偿；同一操作重试复用 Billing requestId，退款
复用 reversalId，失败补收按 retryCount 生成新确定性订单。只有补偿完成才能形成撤销完成事实。

完成事件携带重建后的 proposed 计划：以全部字段 current 值为基线，仅重新应用剩余项目的 proposed 值；目标项目当前
字段差异从查询投影移除，但历史事件保留。目标项目未完成任务通过既有完整前后值任务事件转为 `SKIPPED`，已完成任务和
费用证据不改写。项目投影保存原入账、原资金终态、冲正、逆向资金、错误、重试次数和操作时间，HTTP 只接受操作号、
原因及反向补收所需支付方式。

## M5-07 集成验收与兼容切换

项目撤销开始事件已冻结原操作号、请求摘要和原因；在任何 Billing/Payment 调用前，再通过追加事件补充冻结支付方式和
配置审计信息。恢复
调度只扫描存在上下文且处于 `REQUESTED`、`WAITING_FUNDS` 或未超过最大次数的 `FAILED` 项目；`REQUESTED` 覆盖外部
成功但本地补偿结果未落库的进程崩溃窗口，`WAITING_FUNDS` 继续读取权威资金终态，不能推断或跳过资金门禁。

`MaintenanceItemWithdrawalRecoveryLeasePort` 是 Domain 定义的运行时协调端口，JDBC Adapter 以条件更新取得
`tenantId + maintenanceId + itemCode` 对应项目租约。Application 调度器复用原撤销编排和稳定外部请求，完成或失败后
释放租约；租约超时可被其他节点接管，最大尝试次数阻止永久故障形成无界重试。租约字段只属于查询投影运行状态，不进入
领域事件。

旧 `/web/v1/maintenances/{id}/execute` 通过 `MaintenanceLegacyExecutionFeaturePort` 控制。属性 Adapter 位于
Infrastructure，Application 在案件读取和命令发送前检查；默认开启保持历史兼容，关闭后不再产生新的
`MaintenanceExecutedEvent`。正式独立案件生效、历史查询和已创建案件的其他操作不受开关影响。

M5-07 不修改任何既有事件结构，只追加 `MaintenanceItemWithdrawalRecoveryConfiguredEvent`。因此没有该事件的 M5-06B
在途案件不会自动恢复，避免从历史事实猜测请求原因或支付方式；操作员使用原载荷人工重试一次即可追加恢复上下文。

## 安全与一致性

- Web 和 API 边界使用 Bean Validation，禁止空业务主键和无效数值。
- Admin 代理以认证用户覆盖 `updatedBy`，不信任前端声明的操作人。
- 远程事实逐字段勾稽；不完整或冲突的事实返回业务错误，不写入检查点。
- 详情、搜索、状态变更、执行和 CQRS 查询均按租户隔离。
- 响应只返回费用事实标识与金额，不包含费率明细或敏感精算中间量。
- 配置 Repository 的读取、分页、审计、有效版本解析和冲突检查均以 `tenantId` 为首个条件；管理 API 从认证/请求上下文生成操作上下文，不接受请求体伪造租户、操作者、来源 IP 或关联号。
- Policy 字段目录和规则/权限/模板注册表属于不可信外部边界，必须返回版本化权威证据；空响应、非权威响应、回显不一致或引用缺失均失败关闭。
- Policy 建案快照的结构化字段值是内部审计证据，不直接作为 API 响应透传；案件详情已按字段敏感级别和 `maintenance:sensitive:view` 权限脱敏，生产事件存储仍必须启用静态加密与受控访问。
- 字段草稿写入成功固定返回 `204`，不回显 base/current/proposed 原值；字段数量、编码、对象 ID 和规范化值长度均有限制，未知字段、类型不符和跨项目覆盖在领域边界失败关闭。
- 敏感字段必须同时具备 Policy 掩码策略和 `maintenance:sensitive:view` 权限引用；管理 API 不直接透传数据库中的完整配置或审计 JSON。
- 生命周期审计只与成功状态变更同事务提交。失败尝试、只读访问和敏感查看通过结构化访问日志记录关联号、来源 IP、权限和结果。
- 建案稳定 ID 将租户和受理来源纳入 SHA-256 输入，客户端请求键不出现在 ID 中；相同键的请求指纹比较在聚合内完成，不依赖最终一致读模型。
- Product Offering 远程调用使用固定服务名和固定路径，不接受调用方 URL；Adapter 结构化解析错误响应并校验租户、产品、版本、摘要和保全项编码。正式案件与配置路由先校验 Admin 签发的 access JWT，或校验内部 BFF 共享令牌后才接受操作人和权限头；请求租户必须与认证租户一致。案件详情敏感查看只信任 Spring Security 认证主体权限，不信任未认证请求头自报权限。
- 工作流任务投影的写入和读取均包含 `tenantId`；任务来源仅限聚合冻结项目，不接收调用方提交任务 ID、状态或条件规则。
- 任务写路由只接受受限 DTO，拒绝未知字段；`taskId` 必须属于路径案件，操作人由受信请求头提供，任务前后值和操作载荷摘要由聚合生成。
- 人工来源不能完成业务校验或提交条件结论；外部 API 使用 access JWT，内部 BFF 使用独立共享令牌。生产部署必须通过 `JWT_SECRET` 与 `MAINTENANCE_INTERNAL_TOKEN` 覆盖本地默认值，并限制内部令牌只在服务网络传输。
- 人工审核路由不接受调用方声明审核人，决定人取自受信请求头；自动审核路由不能形成拒绝结论，证据不全只转人工。
- 审核策略与 Product 证据由冻结案件事实派生；身份、材料、金额和风险摘要仍要求生产网关提供服务身份认证和授权。
- Underwriting 是不可信跨域边界；Adapter 使用固定服务契约并校验租户、案件、版本、项目、幂等键和载荷摘要回显，任务投影不得保存原始风险字段值。
- Product 报价是不可信跨域边界；请求摘要由双方独立复算，Adapter 校验完整身份、版本、快照、计算和有效期回显。报价请求拒绝最终结果字段，避免客户端伪造金额或版本。
- Billing 与 Payment 是独立的不可信跨域边界；Application 不以 `POSTED` 推断资金成功，Adapter 分别勾稽 posting、确定性订单和状态，任何冲正或回显不一致均失败关闭。
- Policy 生效回执的字段实际值与案件四值视图使用同一敏感目录脱敏；目录证据缺失时不透传原值。生效路由只接受操作号，租户、操作人取自受信请求上下文，Policy 实际结果不得由调用方提交。
- 未来计划租约查询、人工抢占、案件读取和任务读取均绑定 `tenantId + maintenanceId`；SQL 使用参数绑定，租约 owner 只由服务端生成，调用方不能提交计划时间、租户时区或租约字段。
- 追溯影响分析路由只接受操作号，租户、操作人、保单、范围和影响事实全部由受信上下文及案件/跨域权威接口派生；Policy/Billing/Payment/Claim 均校验保单回显，Policy 额外校验租户回显。
- 追溯分析要求四个权威域完整覆盖并整体失败关闭，防止通过省略某域制造“无影响”结论；分析 ID、请求 hash、证据版本与结果 hash 防止重放换载荷或静默替换证据。
- 影响项使用租户组合唯一键和独立结构化表，查询按租户过滤；跨域摘要仍可能包含业务敏感信息，生产授权应把分析执行与详情查看限制为保全操作/审计角色，并继续依赖网关服务身份保护内部 API。
- 期间重算路由只接受操作号，Product/Billing 请求中的案件、保单、客户、分析版本、计算引用、期间和金额全部由服务端
  权威事实派生；两个 Adapter 校验身份、分析、计算、期间及 hash 回显，不接受调用方声明会计期间开放状态。
- 关闭账期只能产生 `CLOSED_PERIOD_REVIEW` 事实，不能更新历史账单；主投影和逐期间表按租户组合查询，Product 与
  Billing 检查点独立保存，失败恢复不能覆盖已经成功的权威证据；检查点 hash 防止重试时静默换载荷。
- 关闭期间处理路由只接受操作号、目标会计期间和原因；Billing 批次、差额、posting reference 与结果摘要均由服务端
  派生。生效前通过正式查询重新勾稽处理结论，Policy 请求 hash 纳入追溯证据，防止换载荷或伪造处理成功。
- 字段冲突刷新只从 Policy 权威快照派生 current 值，解决路由不能提交当前值或 Policy 版本；冲突事件先于 Policy 调用
  落地。解决详情沿用敏感字段脱敏，自动刷新使用定长操作号防止超长键写入失败。
- 项目撤销路由不能提交金额、方向、posting 或外部结果；聚合先冻结请求，Application 才调用 Billing/Payment。原资金
  最终状态和冲正回执均参与领域勾稽，补偿未完成时不得移除当前字段提案或跳过后续任务。
- 撤销自动恢复只读取服务端冻结上下文。内部调度跨租户扫描到期主键，取得租约后读取记录中的租户并交给原业务编排；
  条件更新绑定项目主键和租约持有者，调用方不能提交租约或租户。恢复复用原操作号，不允许调度器生成新业务载荷。
  `FAILED` 达到最大尝试次数后停止自动重试，保留结构化失败证据供人工处置。
- 旧整案执行开关在读取案件和发送 Axon 命令前判断；关闭路径不泄露案件存在性，也不会留下部分执行事件。
- 旧人工金额入口的部署开关在读取案件和发送命令前判断；关闭路径不泄露案件存在性，也不会留下部分跨域调用或事件。

## 已知限制与后续

1. Policy 当前没有持久化保单生效时的原确认计算引用，调用方需明确传入 `originalCalculationId`。后续应由出单确认事件写入 Policy 引用并由 Maintenance 自动读取。
2. 任务级 `CREDIT` 已接入独立退款单及结果回流；部分退款、多次退款和更细的支付分配仍需在 Payment/Billing 后续阶段扩展。
3. 佣金费用行保留在 Product 差额中，但当前流程不改变 `CommissionPayable`。后续建设独立佣金调整及回拨指令。
4. 当前流程为同步跨域编排，Policy 成功但案件回执失败已有独立补偿事实和人工重试入口；节点崩溃窗口的主动扫描、
   outbox/Saga 监控仍需后续补齐。
5. 独立资源已完成 M3-01 至 M3-08，并开放人工/API 多项创建、字段草稿、案件列表和详情；字段草稿不再接受客户端保单标识。
6. 旧 `MaintenanceChange` 与新字段证据并存。完成新建案、投影和 Policy 回执迁移后，再按事件兼容期废弃旧写入口。
7. 新独立案件已通过正式 Policy API 完成时态生效；旧 `MaintenanceExecutedEvent` 仍为历史重放保留。旧执行入口已具备
   默认开启的灰度开关，生产调用迁移完成后需显式关闭新写入。
8. 当前只有 `policy.holder.mobile` 标记 `executionSupported=true`；其他目录字段仍只能形成拟变更，不能宣称已完成真实回写。
9. 规则、权限和模板的权威只读校验 API 尚未接入；默认失败关闭 Adapter 会阻止配置进入可发布链路。
10. 两个请求完全并发首次创建时，事件存储只允许一个胜出；application 已通过幂等唯一投影校验胜出案件 ID 和指纹，相同载荷可恢复为同一成功响应。
11. M3-04 的创建、逐项加项和初始化完成是顺序 Axon 命令，不是数据库物理事务；所有新旧操作查询已过滤未收到初始化完成事件的独立案件。
12. Maintenance 已定义集合字段的稳定 `objectId:fieldCode` 契约，但 Policy 正式快照当前只生产保单、投保人和主险标量字段；被保人、受益人等集合字段必须由 Policy 查询投影补齐后才能在生产配置中开放。
13. M4-06 已完成 Billing/Payment 双门禁；M5-06B 已支持项目撤销主动调用 Billing 冲正并执行逆向资金。外部系统独立
    发起的冲正仍未通过事件订阅主动触发案件补偿。
14. `MANUAL_REVIEW` 当前只将任务置为 `WAITING_EXTERNAL`；人工核保工作台、队列认领和最终结论回写尚未实现。Rule Engine 主动取证 Adapter 也仍待后续集成。
15. M5-05D 已完成关闭账期人工处理与 Policy 追溯生效；跨域读取不是同一事务快照，因此调用 Policy 前仍必须重新查询
    Billing 权威结论。阻断或待处理影响项当前不能在 Maintenance 内覆盖，只能由权威域变化后重新分析；配置允许的
    项目级独立生效也仍保持关闭。
16. Policy 建案快照完整透传权威状态，Product Offering 是状态适用性的唯一判定来源；Offering 未声明的状态仍失败关闭。
17. M5-06B 首批只允许多项目案件撤销；唯一剩余项目应走案件关闭能力。M5-07 已补齐定时扫描、多节点租约和跨服务
    崩溃窗口自动恢复，但旧在途案件需人工同载荷重试一次补上下文；达到最大次数后的运营异常工作台仍待后续建设。

## 关键决策

| 日期 | 决策 | 理由 |
|---|---|---|
| 2026-08-20 | Product 管价格定义，Billing 管余额事实 | 保持价格所有权与财务所有权分离 |
| 2026-08-20 | Maintenance 只保存跨域检查点 | 保全案件可恢复且不复制价格模型 |
| 2026-08-20 | `POSTED` 不代表资金结算 | 避免将会计事实误报为支付结果 |
| 2026-08-20 | 退款和佣金调整采用独立模型 | 支持分别演进支付结果与佣金应付，不把两类事实塞入保全聚合 |
| 2026-08-24 | 以新增事件渐进迁移多项保全 | 保持既有 Axon 历史事件可重放，避免一次性替换聚合 |
| 2026-08-24 | 字段值采用类型加规范化文本 | 保证跨事件版本稳定序列化，同时拒绝无类型 old/new 字符串 |
| 2026-08-25 | 追溯影响只接受四域权威取证并独立结构化投影 | 防止操作员自报或省略影响，支持后台筛选、审计和事件重放 |
| 2026-08-26 | 项目撤销先冻结请求并完成财务补偿后再退出流程 | 防止跨域调用先于本地事实，也防止撤销项目后遗留有效账务 |
| 2026-08-26 | Product 与 Billing 检查点分离，关闭账期只形成复核事实 | 失败重试复用成功上游事实，禁止为追溯保全改写历史账务 |
| 2026-08-26 | 关闭期间处理独立状态化，Policy 追加记录追溯证据 | 保留历史事件兼容，同时让账务处理、合同版本和权威回执可逐项勾稽 |
| 2026-08-26 | 字段冲突先落事件再阻断 Policy，项目撤销延后绑定财务补偿 | 防止最后写入覆盖，也防止项目撤销后遗留有效账务事实 |
| 2026-08-26 | 撤销恢复上下文使用追加事件，运行时互斥使用数据库租约 | 保持历史事件兼容，同时覆盖多节点和外部成功后本地未落库的崩溃窗口 |
| 2026-08-24 | 配置规则在构造时失败关闭 | 防止无生效步骤、费用门禁冲突或自互斥配置进入案件 |
| 2026-08-24 | Policy 字段目录采用消费端 Port 与防腐 Adapter | 保持 Maintenance Domain 不依赖 Policy API/Infrastructure，并对远程目录失败关闭 |
| 2026-08-24 | 配置采用 Repository 状态聚合并生成规范化内容哈希 | 版本化主数据不进入案件事件流，发布后内容可验证且不可原地修改 |
| 2026-08-24 | 配置写入采用完整 JSON 快照、乐观锁和追加式审计 | 后台可清晰查看变更前后数据，并保证状态与审计同事务提交 |
| 2026-08-24 | 外部引用校验在无权威提供端时失败关闭 | 禁止使用本地假规则、权限或模板放行审批和发布 |
| 2026-08-24 | 配置管理以独立入口、强 ETag 和结构化差异开放 | 后台不依赖保单查询入口，并发更新可检测且审计 JSON 不直接暴露 |
| 2026-08-24 | 配置灰度通过 Domain Port 控制写入，读侧持续可用 | 支持快速停止新配置和首次发布，同时保留排查、审计与在用版本解析能力 |
| 2026-08-24 | 独立建案以租户、来源和客户端请求键稳定定位现有 Maintenance 聚合 | 不新增平行聚合，支持人工/API 重试，并保持旧创建事件结构和历史重放兼容 |
| 2026-08-24 | Policy 建案证据通过消费端 Port 和独立追加事件冻结 | 客户不由调用方声明；正式契约缺失时失败关闭，并保持 M3-01 历史事件可补录、不可覆写 |
| 2026-08-24 | Product 持有版本化保全 Offering，Maintenance 只消费适用性证据 | 产品适用规则不进入保全项定义；建案必须通过 Offering 与已发布配置交集门禁 |
| 2026-08-24 | 多项建案采用项目计划与初始化完成追加事件作为操作门禁 | Axon 多命令不能承诺物理事务；显式完成事实可阻止半成品案件进入后续流程并支持幂等补齐 |
| 2026-08-24 | 项目实例同时冻结 Maintenance 配置与 Product Offering 证据 | 在途案件必须可解释当时为何允许选择，后续配置或 Offering 发布不能改写历史依据 |
| 2026-08-24 | 字段草稿由纯领域规划器基于 before/current/目录证据生成 | 保持 Application 只编排，并让白名单、类型、冲突和快照合并规则可脱离容器测试 |
| 2026-08-24 | proposed 完整结构随追加事件保存并使用 `axon-event://` 引用 | 当前无独立快照存储时仍保留可审计事实，后续可迁移引用而不改变字段差异语义 |
| 2026-08-25 | 工作流任务由聚合基于冻结项目显式实例化并独立投影 | 跳过与条件等待必须成为可重放审计事实，禁止查询侧按当前配置临时推导 |
| 2026-08-25 | 任务操作以统一前后值事件和操作号载荷哈希推进 | 保证状态转换可审计、重试幂等且同项目后继任务不能越序激活 |
| 2026-08-25 | 条件与业务校验证据暂由受信系统 API 提交 | 在 Rule Engine 正式取证契约落地前明确失败关闭边界，避免误宣称 Maintenance 已主动验证外部权威事实 |
| 2026-08-25 | 审核策略从冻结配置回读，自动审核七门禁不全时只转人工 | 防止配置漂移改变在途案件语义，并避免自动证据不足被误记为业务拒绝 |
| 2026-08-25 | 保全核保采用独立正式契约和双侧摘要校验 | 隔离新单核保语义，确保外部结论可幂等恢复且不信任远程回显 |
| 2026-08-25 | 保全报价由 Product 形成最终事实，Maintenance 只保存版本化检查点 | 防止调用方自报金额，并保持价格定义、流程编排和财务入账边界清晰 |
| 2026-08-25 | Product 报价幂等键使用案件/任务/操作三元组 SHA-256 | 在绑定完整业务上下文的同时兼容 Product 现有 64 字符幂等字段 |
| 2026-08-25 | Billing posting 与 Payment 资金结果采用独立证据门禁 | 会计入账不等于真实收退款，必须分别勾稽并支持独立恢复 |
| 2026-08-25 | 旧人工金额入口通过 Domain Port 和属性 Adapter 灰度 | 默认保持兼容，并在停用时于任何读写或外部副作用前失败关闭 |
| 2026-08-25 | 生效状态与流程状态正交，完成只接受 Policy 权威回执 | 避免将任务激活、旧执行事件或跨域调用尝试误报为合同已生效 |
| 2026-08-25 | 立即生效采用“冻结请求事实 → Policy 正式应用 → 记录回执事实” | 保证响应丢失可恢复、异载荷可冲突，并保持跨域调用只在 Application 编排 |
| 2026-08-25 | 未来计划是案件事实，租约是基础设施协调事实 | 保持领域事件可重放，同时用数据库条件更新解决多节点互斥而不污染业务历史 |
| 2026-08-25 | `APPLIED` 是不可回退终态，计划关闭独立幂等恢复 | Policy 权威成功不能因本域计划关闭故障被误报失败或触发重复合同变更 |

## 变更历史

### 2026-08-27 - 保留 Product 报价业务错误

**变更内容**: Product 保全报价适配器解析结构化 Feign 错误响应，向调用方保留业务错误码、可操作消息和 HTTP 状态；无效响应与网络异常仍统一映射为 502。

**变更理由**: 操作人员需要区分定价输入校验失败与服务不可用，不能将所有 Product 422 错误改写为笼统网关异常。

**影响范围**: Maintenance 保全报价错误响应，不改变成功报价、幂等或费用结算契约。

### 2026-08-27 - 状态类保全使用 before 快照作为生效基线

**变更内容**: 无字段提案且存在合同状态动作的保全，以冻结的 before 版本和摘要作为 Policy 生效请求基线；字段型与混合型案件仍强制要求 proposed 快照。

**变更理由**: 中止、恢复和退保等状态交易不生成字段 proposed 快照，但仍需提供可追溯、可校验的 Policy 基准版本与内容摘要。

**影响范围**: 状态类立即/计划生效请求构造；字段变更、冲突检测和空交易保护保持不变。

### 2026-08-27 - 保单状态适用性归属 Product Offering

**变更内容**: Policy 快照适配器、建案编排、领域命令和字段草稿不再硬编码只接受 `EFFECTIVE`，而是完整透传 Policy 权威状态，由 Product Offering 决定当前产品、计划、渠道和时点是否允许建案。

**变更理由**: 中止后的恢复、复效及暂停态退保必须能够取得基准快照；提前拒绝所有非生效状态会绕过正式 Offering 配置并使主流状态保全不可达。

**影响范围**: 独立建案及已批准案件的字段录入状态门禁归属；不放宽 Offering 未声明状态，也不改变 Policy 快照完整性、租户和保单标识校验。

### 2026-08-27 - Billing 冲正状态采用防腐语义映射

**变更内容**: Billing 防腐适配器只接受正式冲正契约的 `POSTED` 成功状态，并在进入 Maintenance 领域前映射为本域 `REVERSED` 证据；其他状态继续按跨域契约错误失败关闭。

**变更理由**: Billing 的 `POSTED` 表示逆向账务事实已入账，Maintenance 的 `REVERSED` 表示原保全入账已完成冲正；直接透传会把两个限界上下文的不同状态词误判为无效证据。

**影响范围**: 已产生 Billing 入账的多项目撤销；不改变冲正方向、金额、币种、原 posting 摘要及 Payment 逆向资金勾稽。

### 2026-08-27 - 费用结算按外部状态保存稳定检查点

**变更内容**: 费用结算恢复时先校验 Billing posting 不漂移；Payment 状态未变化则直接返回既有检查点，状态推进时使用由客户端操作号、posting 和权威资金状态派生的稳定阶段操作号追加新事实。

**变更理由**: 资金异步从 `PENDING/PROCESSING` 推进到终态时，证据内容必然变化，不能把同一客户端操作号误判为异载荷，也不能因记录时间变化重复追加相同状态事件。

**影响范围**: 补收/退费的轮询恢复与幂等重试；不会重复创建 Billing posting 或 Payment 订单，也不允许恢复时替换原账务事实。

### 2026-08-25 - M4-01 流程任务基座

**变更内容**：新增任务领域模型、初始化与显式回填事件、租户隔离任务投影、案件详情映射和 Liquibase 回滚测试。

**变更理由**：将冻结步骤配置转为可执行流程事实，为后续条件决策、审核、核保和费用门禁提供稳定状态基础。

**影响范围**：Maintenance Domain、Query、Application、Web、Bootstrap 及案件详情契约；历史事件结构保持不变。

**决策依据**：任务状态属于聚合事实，旧案件可为空且仅通过显式命令回填，避免重放时受当前配置漂移影响。

### 2026-08-25 - M4-02 任务推进与条件决策

**变更内容**：新增领取、开始、完成、失败、重试和条件决策命令，统一任务前后值事件、同项目后继激活、结构化任务详情、人工/API 路由及任务审计字段迁移。

**变更理由**：将 M4-01 静态任务事实转为可操作、可恢复、可解释的流程，同时为审核、核保、费用和生效专用门禁预留边界。

**影响范围**：Maintenance Common、Domain、Application、Query、Web、Bootstrap、案件详情契约和任务投影表；M4-01 初始化事件保持兼容。

**决策依据**：聚合拥有任务顺序和转换事实，Application 负责租户及来源门禁；外部规则证据在真实 Adapter 接入前按受信系统 API 边界显式管理。

### 2026-08-25 - M4-03 人工审核与 API 自动审核

**变更内容**：新增人工与自动审核命令、七类门禁证据、审核拒绝终态事件、冻结配置勾稽、独立 HTTP 路由、任务与案件投影字段及 Liquibase 回滚测试。

**变更理由**：让后台操作员可清晰处理审核，同时使 API 自动审核具备可解释、可接管、可重试且不能绕过门禁的业务语义。

**影响范围**：Maintenance Common、Domain、Application、Query、Web、Bootstrap、案件详情契约和任务投影表；既有初始化与任务转换事件保持兼容。

**决策依据**：Domain 保留审核状态机和证据不变量，Application 解析冻结策略与来源门禁；自动证据不足不等同业务拒绝，必须保留人工接管。

### 2026-08-25 - M4-04 Underwriting 风险结论

**变更内容**：新增保全核保 Port/Adapter、跨域正式 API、确定性核保案件、版本化结论、拒绝终态、人工等待状态、任务/案件投影、HTTP 路由和 Liquibase 回滚测试。

**变更理由**：让保全核保成为独立、可追溯、可恢复的流程门禁，避免复用新单核保契约或由调用方自报结论。

**影响范围**：Maintenance 与 Underwriting 的 Domain、Application、Infrastructure、Query、Web/API 和测试；既有任务事件保持追加式兼容。

**决策依据**：Application 负责取数和跨域编排，Domain 保留状态机不变量，Infrastructure 校验外部回显；敏感原值不进入任务投影或摘要。

### 2026-08-25 - M4-05 Product 版本化报价

**变更内容**：新增 Product 保全报价 Port/Adapter 与正式 API、完整载荷摘要、严格计划版本门禁、报价状态和证据、人工/API HTTP 路由、任务详情投影及 Liquibase 更新/回滚测试。

**变更理由**：让费用任务从冻结案件事实取得可追溯报价，最终金额、方向和版本由 Product 决定，同时为 M4-06 Billing/Payment 门禁保留独立检查点。

**影响范围**：Maintenance 与 Product 的 Common/Domain/Application/Infrastructure/Query/Web/API、任务投影表和跨层测试；Policy/Billing/Payment 未修改。

**决策依据**：Application 编排取数，Domain 保持任务状态和幂等不变量，Infrastructure 校验外部回显；`QUOTED` 与费用完成分离，避免未经入账和资金确认进入生效。

### 2026-08-25 - M4-06 Billing 与 Payment 门禁

**变更内容**：新增任务级权威结算编排、Billing posting 与 Payment 资金独立证据、收款/退款/无差额分支、失败重试、冲正门禁、任务详情投影和 Liquibase 更新/回滚测试。

**变更理由**：将 Product 报价转为可恢复的会计和资金事实，确保费用任务不能只凭 Billing 入账越过真实收退款门禁。

**影响范围**：Maintenance、Billing、Payment 的 Domain/Application/Infrastructure/Query/Web/API、任务投影和跨域测试；既有报价及历史事件保持兼容。

**决策依据**：Application 编排跨域取证，聚合保存状态与检查点，Adapter 逐字段勾稽；posting 和资金结果分别记录，便于失败恢复和后台审计。

### 2026-08-25 - M4-07 集成验收与兼容切换

**变更内容**：新增全命令序列、双保全项交错、投影重建和旧事件重放验收；为旧人工金额入口增加默认开启的部署级灰度开关，并补充七仓兼容构建证据。

**变更理由**：在进入 Policy 真实生效前验证 Phase 4 跨域链路闭环，并为仍接受人工金额的历史入口提供可控退出机制。

**影响范围**：Maintenance Application/Domain/Infrastructure/Bootstrap 与集成测试；任务级结算、旧无金额权威结算、查询和历史事件不受开关影响。

**决策依据**：兼容开关定义为 Domain Port、属性实现位于 Infrastructure，Application 在任何读取或副作用前判断；默认开启保持向后兼容。

### 2026-08-25 - M5-01 生效状态与 Policy 回执证据基座

**变更内容**：新增正交生效状态、请求/成功/失败专用命令与证据、事件溯源状态转换、案件和任务投影、实际字段与 applied 快照回填、查询/脱敏响应及 Liquibase 更新回滚测试。

**变更理由**：先建立可重放、可查询、可失败恢复的合同生效事实，再在 M5-02 接入 Policy 真实写入，避免旧执行事件或调用成功被误记为保单已变更。

**影响范围**：Maintenance Common、Domain、Query、Application、Web、Bootstrap、案件列表/详情契约和任务投影；生产生效 HTTP 与 Policy 写 API 未开放。

**决策依据**：领域状态只由追加事件重放；Policy 回执必须勾稽请求和版本，并以实际字段值、批单及 applied 快照共同构成权威成功证据。

### 2026-08-25 - M5-02 Policy 正式应用与立即生效

**变更内容**：新增 Policy 正式应用契约与防腐 Adapter、立即生效 Application 编排、人工/API 路由、稳定请求摘要、
回执勾稽及失败事实；首批真实执行投保人手机号。

**变更理由**：将 M5-01 的证据状态机接到真实 Policy 合同变更，同时保证响应丢失重试不会重复生成批单。

**影响范围**：Maintenance Domain Port、Application、Infrastructure、Web/API 和测试，以及 Policy API、聚合、字段目录、
批单投影和查询快照；历史事件保持不变。

**决策依据**：跨域调用只在 Application 编排，Policy 拥有合同版本与实际结果；多项目原子和时态交易未具备完整
补偿边界前失败关闭。

### 2026-08-25 - M5-03 案件级原子生效与状态交易

**变更内容**：新增案件级生效命令、Policy 状态动作与单事件应用、状态前后值回执、任务/案件查询证据、独立补偿事实、
人工幂等重试闭环及两组 Liquibase 正反迁移。

**变更理由**：避免多保全项逐任务提交形成部分生效，并让退保、暂停、恢复和复效与字段型保全共享版本、批单、快照和
幂等语义；同时显式覆盖 Policy 已成功但本域回执失败的跨域窗口。

**影响范围**：Metadata、Maintenance Domain/Application/Infrastructure/Query/Bootstrap 及 Policy API/Domain/Query/Web。

**决策依据**：Policy 聚合先校验后追加一个权威事件，Maintenance 聚合在一个命令事务中更新全部任务；项目级独立生效
在缺少显式配置事实时保持关闭。

### 2026-08-25 - M5-04 未来生效计划与可靠调度

**变更内容**：新增案件级未来计划聚合事实、UTC/租户时区语义、数据库租约、到期扫描、退避重试、暂停/恢复/立即执行
路由、Policy/Billing/Payment 权威重校验、案件详情投影及 Liquibase 正反迁移。

**变更理由**：让未来、指定日、下一缴费日和保单周年日保全可在多节点环境中可靠执行，并避免到期时继续使用已漂移的
保单版本、过期报价或失效资金检查点。

**影响范围**：Policy API/Domain/Query 快照日期，Maintenance Common/Domain/Application/Infrastructure/Query/Web/
Bootstrap，案件主投影表、配置和跨层测试；M5-01 至 M5-03 历史事件保持不变。

**决策依据**：业务计划进入聚合事件，运行时租约留在 Infrastructure；`APPLIED` 不可回退，Policy 成功后的计划关闭
使用原回执幂等恢复，流程未就绪只退避重试。

### 2026-08-25 - M5-05A 追溯建案边界与 Product 定价时点

**变更内容**：新增冻结配置生效模式与日期边界校验，按租户时区校验追溯/未来自然日上限，并将追溯案件指定时点传给
Product 报价；正式生效保留独立追溯影响门禁。

**变更理由**：先保证进入追溯流程的案件与价格证据使用一致业务时点，再建设影响分析和期间重算，避免无效案件或错误
定价进入跨域副作用阶段。

**影响范围**：Maintenance Domain 生效规则、Application 建案与报价/生效编排及定向测试；无数据库、事件和 HTTP
契约变更。

**决策依据**：配置拥有可选模式和天数上限，租户时区定义业务自然日，Product 拥有价格，Maintenance 在完整影响证据
形成前不得调用 Policy。

### 2026-08-25 - M5-05B 追溯影响分析证据与后台清单

**变更内容**：新增可重放的追溯影响分析状态机，接入 Policy、Billing、Payment、Claim 四域正式取证 Adapter，开放
API/Web 双路由，并将分析摘要和结构化影响项投影到案件详情及独立数据表。

**变更理由**：在期间重算和合同回写前形成不可由操作员伪造的跨域影响基线，让后台能够清晰核对后续批单、账务、
资金和理赔事实，同时保留失败恢复和审计证据。

**影响范围**：Maintenance Common/Domain/Application/Infrastructure/Query/Web/Bootstrap，Policy、Billing、Payment、
Claim API Adapter，案件主投影与 `t_maintenance_retroactive_impact_view`；M5-05A 及更早事件保持兼容。

**决策依据**：Web 仅依赖 Application command 门面；四域完整覆盖和远端回显校验失败关闭；`COMPLETED` 仅代表取证
完整，该批不解除 Policy 追溯生效硬门禁。

### 2026-08-26 - M5-05C Product/Billing 追溯期间重算

**变更内容**：新增 Product 版本化逐期间价格差额、Billing 会计期间调整批次、Maintenance 两段检查点状态机、失败
恢复编排、API/Web 双路由、主投影摘要及逐期间 before/after 结构化明细。

**变更理由**：在追溯合同应用前形成可重放、可勾稽的价格与账务事实；关闭账期通过显式人工处理状态承接，避免修改
历史账单，同时让 Billing 失败重试无需重复 Product 重算。

**影响范围**：Product/Billing 正式 API、Domain/Application/Infrastructure 与 Liquibase；Maintenance
Common/Domain/Application/Infrastructure/Query/Web/Bootstrap。既有 M5-05B 及更早事件保持不变。

**决策依据**：价格差异归 Product、会计期间和账务事实归 Billing、流程与检查点归 Maintenance；`REVIEW_REQUIRED`
不代表允许生效，该批继续保留追溯硬门禁。

### 2026-08-26 - M5-05D 关闭期间处理与 Policy 追溯生效

**变更内容**：新增 Billing 关闭期间差额结转及权威查询契约、Maintenance 处理状态机与 API/Web 双路由、主/逐期间
投影、完整追溯生效资格门禁，以及 Policy 追溯证据请求、追加事件和权威回执。

**变更理由**：在不改写历史账单和既有 Policy 应用事件的前提下，让操作员能够处理关闭期间，并保证合同追溯版本只在
分析、重算、账务处理和逐期间证据全部一致时生效。

**影响范围**：Billing、Maintenance、Policy 的 API/Domain/Application/Infrastructure/Query/Web、Liquibase 与跨层
测试；既有字段/状态应用事件和非追溯请求保持兼容。

**决策依据**：账务结转和 posting reference 归 Billing，案件状态和跨域勾稽归 Maintenance，合同版本与批单归 Policy；
只对远端调用/事实失败记录 `FAILED`，本地完成命令超时依赖稳定操作号重试。

### 2026-08-26 - M5-06A 顺序外字段冲突与显式解决

**变更内容**：新增 Policy 最新快照冲突刷新、字段级采用当前/拟值/重新录入、案件 proposed 快照重建、生效前自动
版本检查、冲突审计投影、API/Web 双路由及 Liquibase 正反迁移。

**变更理由**：避免建案后 Policy 被其他交易修改时发生最后写入覆盖，并让操作员在后台明确看到和解决每个冲突字段。

**影响范围**：Maintenance Common/Domain/Application/Infrastructure/Query/Web/Bootstrap；只追加冲突刷新与解决事件，
既有建案、任务和生效事件保持不变。

**决策依据**：Policy 拥有 current 值和合同版本，Application 负责权威取数，聚合负责冲突规则与审计；项目撤销涉及已
形成的账务和资金事实，延后到 M5-06B 与财务补偿一并实现。

### 2026-08-26 - M5-06B 项目撤销与财务补偿

**变更内容**：新增项目撤销请求/补偿/失败状态机、Billing 正式冲正 Port、Payment 独立退款与补收编排、原资金终态
门禁、proposed 快照重建、任务显式跳过、项目审计投影、API/Web 双路由及 Liquibase 正反迁移。

**变更理由**：允许操作员撤回尚未生效的多项目案件项目，同时保证已形成的账务和资金事实得到方向相反且可对账的补偿，
避免直接删除项目、字段或任务造成合同视图与财务视图不一致。

**影响范围**：Billing API/Domain/Application/Infrastructure/Web/Bootstrap 和 Maintenance
Common/Domain/Application/Infrastructure/Query/Web/Bootstrap；既有 posting、项目加入、字段变化和任务转换事件不修改。

**决策依据**：Billing 拥有不可变账务冲正，Payment 拥有真实退款/收款执行，Maintenance 拥有撤销资格、跨域编排与
最终完成门禁；外部结果逐字段勾稽，补偿失败保存已取得证据并通过稳定操作号恢复。

### 2026-08-26 - M5-07 集成验收与旧执行入口切换

**变更内容**：新增项目撤销恢复上下文追加事件、数据库条件更新租约、定时扫描与最大尝试次数，并为旧整案执行入口增加
默认开启的部署级灰度开关；完成 Maintenance、Policy、Product、Billing、Payment 五域全量验证。

**变更理由**：覆盖 Billing/Payment 成功后进程崩溃、本地检查点未落库以及多节点重复恢复窗口，同时让历史执行入口可在
调用方迁移完成后停止新写入，而不破坏旧事件重放和查询。

**影响范围**：Maintenance Domain/Application/Infrastructure/Query/Bootstrap、项目查询投影和 Liquibase；Policy、
Product、Billing、Payment 仅执行兼容验证，既有跨域契约与历史事件结构不修改。

**决策依据**：恢复业务上下文进入追加事件，运行时租约留在 Infrastructure，Application 复用原幂等撤销编排；旧入口
开关通过 Domain Port 注入并在所有读取、副作用前失败关闭。

### 2026-08-28 - 集合字段必填值按合并结果校验

**变更内容**：字段草稿冻结 Policy 目录中属于当前保全项的全部字段描述；`MaintenanceFieldProposalPlanner` 将无条件
必填规则改为校验 current 与本次提案合并后的完整 proposed 快照。标量字段直接校验结果值；集合字段按本次触达对象的
对象类型和稳定 `objectId` 校验结果值。

**变更理由**：已有集合对象的未变必填字段不应要求重复提交，否则会与“拟值不能等于基准值”门禁形成死锁；新增集合
对象仍必须在同一草稿中补齐全部必填值。

**影响范围**：Maintenance Application/Domain 字段目录冻结、字段提案规划及回归测试；HTTP 契约、事件结构和历史事件
重放不变。

**决策依据**：`required` 约束的是变更完成后的业务对象完整性，而不是请求中重复出现字段；完整 proposed 快照是后续审核、
冲突检测和 Policy 生效的权威输入。

### 2026-08-28 - 草稿删除保留独立审计历史

**变更内容**：通过增量 Liquibase 迁移移除配置审计表到当前配置表的外键；`configuration_id` 继续作为历史业务引用和
租户内序号唯一键组成部分，草稿删除仍先追加 `DRAFT_DELETED` 审计再删除当前快照。

**变更理由**：原外键使任何带审计记录的草稿都无法删除，并与“删除后保留最终审计”的应用流程互相矛盾。

**影响范围**：Maintenance Bootstrap 数据库约束和 Liquibase 正反迁移测试；配置 HTTP 契约、审计结构和已发布配置不变。

**决策依据**：审计记录是不可随当前快照删除的历史事实，不能使用级联删除；删除当前配置后允许审计记录以业务标识独立
留存，回滚迁移会恢复原外键。

### 2026-08-28 - 正式案件 API 认证失败关闭

**变更内容**：为正式案件、后台案件和配置管理路由统一增加认证门禁；外部调用校验 Admin access JWT，内部 BFF 调用校验
共享令牌后建立 Spring Security 身份，并由拦截器校验认证租户与请求租户一致。无凭据、无效令牌和伪造身份头均保持匿名并
返回 401，权限不足返回 403。

**变更理由**：租户头和操作人头只能承载已认证身份的上下文，不能独立构成认证，否则可猜测租户和操作者的未认证请求会
读取或修改保全案件。

**影响范围**：Maintenance Web/Bootstrap 认证配置、Admin BFF 内部调用配置及接口测试；业务 HTTP 路径和请求体契约不变。

**安全边界与已知风险**：JWT 只接受 `tokenType=access` 且使用共享 HMAC 密钥验签；内部令牌只允许服务网络中的 Admin BFF
使用。生产必须覆盖 `JWT_SECRET` 和 `MAINTENANCE_INTERNAL_TOKEN` 本地默认值，并通过密钥管理系统轮换；当前实现不承担
网关级令牌吊销，仍依赖 access token 短有效期和 Admin 统一登出策略。

### 2026-08-28 - 保全字段格式规则与后台字典展示

**变更内容**：保全字段白名单新增 `NONE/EMAIL/MOBILE_CN/GENDER/ID_CARD_CN/POSTAL_CODE_CN/CUSTOM_REGEX`
格式规则，配置同时保存自定义表达式和校验提示。字段草稿规划器在生成拟变更快照前执行格式校验，格式规则纳入配置内容哈希。
后台保全配置编辑器通过 Admin 字典展示字段类型、格式规则、流程步骤、费用模式和生效方式。

**变更理由**：字段是否允许修改和修改值格式都必须来自已审批、已冻结的保全项配置；仅在前端校验会被 API 调用绕过，
硬编码页面中文映射也无法支持租户排序、启停和国际化。

**影响范围**：Maintenance Common/Domain/Web 配置契约、字段提案规划及单元测试，Admin 字典种子和管理后台展示。
历史配置缺少格式字段时按 `NONE` 读取，现有建案和事件结构保持兼容。

**边界与安全**：Admin 字典只控制标签、排序、启停和 i18n，不参与 Maintenance 状态机或字段校验决策。自定义正则限制为
256 字符并在配置校验阶段编译，待匹配规范值限制为 65,536 字符；预置与自定义规则统一使用 RE2/J 线性时间引擎，消除
灾难性回溯导致的拒绝服务风险。RE2/J 不支持反向引用、环视等依赖回溯的 Java 正则语法，配置保存时会失败关闭；字段值校验
失败不会生成 proposed 快照。
