# Titanium Maintenance

保全域负责保单生效后的变更案件管理，包括案件创建、变更项、算费检查点、审核、执行和查询。模块采用 Axon 事件溯源与独立 CQRS 读模型，并通过领域 Port 调用 Policy、Customer、Underwriting、Product、Billing 和 Payment。

## 核心能力

- 保全案件生命周期：创建、状态变更、执行和终态保护。
- 独立建案基座：人工/API 来源隔离、租户级稳定案件 ID、Policy 权威快照冻结、Product Offering 与已发布配置双重门禁、有序多项选择、同键同载荷幂等和异载荷冲突保护。
- 多项保全领域基座：保全项定义、版本冻结、兼容校验和字段白名单。
- 配置版本主数据：保全项草稿、审批、发布、退役、修订、有效期、追加审计和内容哈希。
- 配置发布门禁：按 Policy 权威字段目录校验字段能力、类型、敏感权限，并校验规则、权限和模板引用。
- 变更证据：强类型 `base/current/proposed/applied` 字段值、三快照引用和顺序外冲突状态。
- 动态字段草稿：按冻结配置白名单和 Policy 权威字段目录接收结构化值，生成完整 proposed 快照、逐字段差异、配置来源和敏感掩码证据。
- 流程任务管理：按冻结项目和步骤生成稳定任务，支持领取、开始、完成、失败、重试和条件决策，并通过独立租户投影返回完整操作证据。
- 保全核保编排：基于冻结案件和结构化风险差异调用 Underwriting 正式契约，保存版本化结论并支持拒绝终止、人工等待和幂等重试。
- 版本化保全报价：基于冻结 before/proposed 快照、产品/计划版本和完整定价输入调用 Product，保存原/替代计算、内容寻址版本、方向、金额、摘要和有效期。
- 多租户隔离：命令、事件、查询、读模型和远程调用均携带 `tenantId`。
- 保费差额登记：以原确认计算和结构化替代输入驱动 Product 重新计算及生命周期差额。
- 财务双门禁：分别记录 Billing 入账与 Payment 收款/退款证据；仅入账和资金均成功或无需处理时完成费用任务。
- 正式立即生效：多项目默认整案原子提交 Policy；字段型与暂停、恢复、复效、终止/退保均返回批单、版本、实际值、状态前后值和 applied 快照，失败不会形成部分完成。
- 生效补偿：Policy 已成功但案件回执写入失败时保留独立补偿事实，人工重试原生效入口幂等勾稽原批单并自动关闭补偿标记。
- 追溯影响取证：按冻结配置和租户自然日校验追溯建案，以追溯时点调用 Product 报价，并从 Policy、Billing、Payment、Claim 四个权威域生成可重放、结构化影响清单；阻断或待处理项不允许进入生效。
- 追溯期间重算与生效：Product 按受影响账单期间生成版本化 before/after 差额，Billing 按开放会计期间登记调整；关闭期间经独立状态机结转至开放期间并保存 posting reference，完整跨域证据勾稽后由 Policy 记录追溯证据并应用合同版本。
- 幂等恢复：Product、Billing 与 Payment 分别写入事件检查点，失败重试复用已经成功的外部事实。

## 费用结算语义

```text
Maintenance
  -> Product MAINTENANCE 替代计算
  -> Product 生命周期差额
  -> Maintenance Product 检查点
  -> Billing 余额事实
  -> Maintenance Billing 检查点
  -> Payment 收款/退款事实
  -> Maintenance 资金检查点
  -> FEE_SETTLEMENT 完成并激活同项目 EFFECT
```

| 状态/方向 | 含义 |
|---|---|
| `DEBIT` | 新增客户应收事实已登记，不代表已收款 |
| `CREDIT` | 客户贷方余额或退款资格已登记，不代表已退款 |
| `NONE` | 本次无客户差额，状态为 `NOT_REQUIRED` |
| `POSTED` | Product 差额与 Billing 入账已勾稽，仍需独立资金证据 |
| `PENDING` | Payment 正在处理，费用任务进入 `WAITING_EXTERNAL` |
| `SUCCEEDED/NOT_REQUIRED` | 资金门禁完成，可完成费用任务 |
| `FAILED/REVERSED` | 费用任务失败，必须受控重试或补偿 |

`DEBIT` 通过 Payment 确定性收款单处理，`CREDIT` 复用 Billing 创建的独立退款单，`NONE` 记录无需资金处理且不调用 Payment。佣金补付或回拨仍由后续 `CommissionAdjustment/ClawbackInstruction` 承接，本模块不自动修改佣金应付。

任务进入 `QUOTED` 只代表已取得有效 Product 报价。调用任务级 `premium-settlements` 后，Maintenance 逐项勾稽
Billing posting 与 Payment 资金结果；只有 `POSTED + SUCCEEDED/NOT_REQUIRED` 才完成 `FEE_SETTLEMENT` 并激活同一保全项的 `EFFECT`，不会跨项目串行激活。

## API

> 独立保全资源已完成 M3-01 至 M3-08、流程任务 M4-01 至 M4-07，以及 M5-01 至 M5-07 的立即、未来、追溯、正式生效、顺序外字段冲突、项目撤销财务补偿和失败自动恢复能力。应用层先读取 Policy 权威快照，再一次性解析 Product 正式 Offering 和每个所选项目的已发布配置；客户标识只从 Policy 快照派生。人工/API 建案、字段草稿、案件列表、详情、任务操作、审核、核保、报价、结算、冲突解决、项目撤销和时态生效路由均位于独立保全资源。配置管理与案件操作始终独立于保单查询入口。

### 独立建案 API

```http
POST /api/v1/maintenance/cases
POST /web/v1/maintenance/cases
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
Content-Type: application/json
```

新请求使用有序 `itemCodes`；`itemCodes` 与旧 `maintenanceType` 必须且只能提供一种。退保别名
`SURRENDER`、`POLICY_SURRENDER` 统一映射为 `POLICY_TERMINATION`。建案按以下顺序执行：

```text
Policy 权威快照
  -> Product Offering + 全部已发布配置解析
  -> 创建案件并记录项目计划
  -> 逐项冻结配置与 Offering 证据
  -> 初始化完成
  -> 201 Created
```

任一项目不适用、渠道不支持、`atomicOnly` 或双向互斥冲突都会失败关闭。多命令流程不宣称数据库物理事务；只有收到初始化完成事件的案件才可进入字段录入和操作后台查询。相同幂等键完全并发首次创建时，失败请求会通过唯一投影定位并校验胜出案件指纹，再继续初始化。

### 旧建案兼容切换

旧 `POST /web/v1/maintenances` 与服务间 `POST /api/v1/maintenances` 仍可兼容建案，但由部署属性
`titanium.maintenance.legacy-creation-enabled` 控制，默认 `true`。关闭后 Application 在调用 Policy、Customer、
读模型或 CommandGateway 前返回 503 `MAINTENANCE_LEGACY_CREATION_DISABLED`；旧案件查询、状态流转、执行和结算不受影响。独立 `/maintenance/cases` 建案不经过该开关。

### 案件查询 API

```http
GET /api/v1/maintenance/cases
GET /web/v1/maintenance/cases
GET /api/v1/maintenance/cases/{caseId}
GET /web/v1/maintenance/cases/{caseId}
X-Tenant-Id: tenant-id
```

列表支持案件号、保单号、客户、项目、来源、状态、操作人和创建时间范围筛选，分页在完整过滤与计数后执行。详情返回案件、冻结项目配置/Offering 证据、`base/current/proposed/applied` 字段差异、三类快照引用，以及按项目和步骤排序的 `workflowTasks`。独立案件必须初始化完成才可见，旧兼容查询和统计使用相同门禁；字段值默认按目录策略脱敏，只有认证主体持有 `maintenance:sensitive:view` 才返回敏感原值。

### 流程任务操作

新独立案件完成初始化后，聚合按每个冻结项目的步骤配置生成任务：`CREATE` 记为 `COMPLETED`，配置为
`SKIPPED` 的步骤保留为 `SKIPPED`，首个可执行必需步骤进入 `READY`，首个条件步骤进入
`WAITING_CONDITION`，后续步骤进入 `PENDING`。任务 ID 固定为 `caseId:itemCode:stepType`。

只有 Phase 3 历史事件的案件允许详情返回空任务列表，不从当前配置推导历史任务。运维可通过显式
`InitializeMaintenanceWorkflowCommand` 基于案件已冻结项目幂等回填。M4-01 初始化事件缺少 M4-02 新增审计字段时，
反序列化为未领取、零重试且无操作证据的兼容状态。

```http
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/claim
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/start
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/fail
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/retry
POST /api/v1/maintenance/cases/{caseId}/tasks/{taskId}/condition-decision
POST /web/v1/maintenance/cases/{caseId}/tasks/{taskId}/review-decision
POST /api/v1/maintenance/cases/{caseId}/tasks/{taskId}/auto-review
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/underwriting-assessment
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-quotes
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-settlements
POST /api|web/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
```

领取和开始请求只提交 `operationId`；失败增加 `failureCode/failureReason`，重试增加 `reason`。完成
`DATA_ENTRY` 可只提交操作号，完成 `VALIDATION` 必须由系统 API 同时提交 `evidenceVersion`、64 位
`evidenceHash` 和 `resultCode`。条件决策仅开放系统 API，提交 `ruleVersion/inputHash/decision/reason`，其中
`decision` 为 `EXECUTE` 或 `SKIP`。

任务必须按同一保全项的步骤顺序推进；完成或条件跳过才会激活该项目的下一任务。`REVIEW`、`UNDERWRITING`、
`FEE_SETTLEMENT` 和 `EFFECT` 不能通过通用完成接口绕过后续专用门禁。相同 `operationId` 和载荷幂等，异载荷
冲突；详情中的 `workflowTasks` 返回领取、重试、失败、条件规则以及最后操作的结构化前后证据。

人工审核必须先领取并开始 `REVIEW` 任务，仅当前领取人可提交 `APPROVE/REJECT`，且建案人与审核人必须分离。
审核使用项目冻结的配置 ID、版本和内容 hash 回读审批策略，不解析当前有效配置。人工拒绝将任务和案件同时置为
`REJECTED`，后续任务不会激活。

API 自动审核只允许通过结论。渠道、Product Offering、冻结配置、身份、材料、金额和风险七类门禁全部通过后，
聚合才保存结构化门禁证据；任一证据缺失或任务已被人工领取时返回 `MANUAL_REQUIRED`，不写拒绝事件，保留人工
接管能力。相同操作号和载荷可安全重试；审核摘要不包含服务端决定时间，避免响应丢失后的等价重试产生新摘要。

当前 `VALIDATION` 和条件决策证据由受信系统 API 提交，Maintenance 尚未直接调用 Rule Engine 获取证据；
生产启用自动决策前必须接入真实 Port/Adapter，并由服务身份认证保证该路由不能被普通调用方伪造。
自动审核中的身份、材料、金额和风险摘要同样由受信 API 提交，生产启用前必须由网关服务身份与细粒度授权保护；
配置和 Product Offering 证据则始终从案件冻结事实派生，调用方不能覆盖。

核保评估由 Application 从租户隔离的案件、项目和字段投影组装请求，不接受调用方填写核保结论。请求包含保单基准
版本、产品/计划版本、冻结配置证据、结构化风险字段差异和稳定幂等键；存在未解决字段冲突时不会调用 Underwriting。
Adapter 会校验租户、案件、保单版本、项目、幂等键和请求摘要回显，不一致时失败关闭。

`APPROVED/CONDITIONAL_APPROVED` 完成核保任务并激活后继，`REJECTED` 同时终止任务和案件，
`MANUAL_REVIEW` 进入 `WAITING_EXTERNAL`。远程异常不写任务事件，可使用同一外部幂等键重试；`NOT_REQUIRED`
只允许记录到配置为 `SKIPPED` 且没有风险差异的核保任务。详情仅保存核保案件号、请求摘要、规则/模型版本、
结论、附加条件、脱敏摘要和完成时间，不保存原始风险字段值。当前未提供人工核保工作台及最终结论回写接口。

保全报价路由只接受 `operationId`、`originalCalculationId`、生命周期意图和结构化定价输入，不接受 `amount`、
`direction`、`quoteVersion` 或 `validUntil` 等 Product 结果字段，任何未声明字段均返回 400。Application 使用案件冻结的
保单基准版本、产品/计划版本、before/proposed 快照和项目证据补齐请求；Maintenance 与 Product 共同复算完整载荷
SHA-256，Adapter 再逐字段校验远端回显和 24 小时有效期。

Product 数据库幂等字段长度为 64，跨域幂等键固定为
`SHA-256(maintenanceId + ":" + taskId + ":" + operationId)`，既绑定案件/任务/操作三元组又保持字段兼容。
报价成功时 `quoteVersion` 等于 Product 生命周期差额 `resultHash`，任务进入 `QUOTED`；新 `operationId` 可重报价并
替换当前投影，旧报价事件仍保留。`feeMode=NONE` 或 `OPTIONAL + SKIP` 直接记录 `NOT_REQUIRED`，不调用 Product、
Billing 或 Payment。Policy 正式快照尚无原确认计算引用，因此当前调用方仍必须提供 `originalCalculationId`；最终
金额、方向、版本、摘要和有效期始终由 Product 决定。

任务级收退费请求只接受 `operationId`、支付方式和原因，不接受金额、方向或币种。Maintenance 使用当前有效报价
调用 Billing 幂等入账，再按方向调用或查询 Payment；处理中进入 `WAITING_EXTERNAL`，失败或 Billing 冲正进入
`FAILED`。重试保留成功 posting 和历史资金检查点，重复回调不会重复完成任务。详情分别返回
`billingPostingEvidence` 和 `fundSettlementEvidence`，便于操作人员核对会计事实与真实资金状态。

立即生效路由只接受 `operationId`，保单、字段变化、期望版本、proposed 快照摘要和生效时间全部从租户隔离案件证据
组装。Application 对全部非跳过 `EFFECT` 任务冻结同一案件请求，再调用 Policy 正式应用 API 一次并统一记录回执；
任一任务前置状态不一致即在跨域调用前失败。稳定请求 ID 由租户和案件派生，响应丢失后同载荷重试返回同一批单。

字段型保全仍要求实时 Policy 字段目录声明 `executionSupported=true`，当前首批只有 `policy.holder.mobile`。暂停、恢复、
复效、终止和退保可不包含字段变化，回执会返回 `stateAction/statusBefore/statusAfter`。Policy 成功而案件回执写入失败时，
案件详情的 `effectCompensation` 暴露待处理事实；操作员再次调用同一生效路由完成勾稽。

`FUTURE`、`SPECIFIED_DATE`、`NEXT_BILLING_DATE` 和 `POLICY_ANNIVERSARY` 在建案完成后自动建立未来生效计划。计划冻结
租户时区，执行时间按 UTC 存储；定时扫描和人工立即执行使用同一数据库租约，避免多节点重复执行。到期会重新读取
Policy 版本，并对已有 Product 报价、Billing posting 和 Payment 资金事实重新勾稽。流程尚未走到 `EFFECT` 时退避重试，
Policy 版本漂移或事实不一致时失败关闭。已取得 Policy 权威回执后，计划关闭失败只重试关闭，不回退 `APPLIED` 或重复
提交合同变更。

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api|web/v1/maintenance/cases/{caseId}/effect-schedule` | 为历史未来案件幂等补建计划 |
| `POST` | `/api|web/v1/maintenance/cases/{caseId}/effect-schedule/pause` | 暂停待执行计划，必须提供原因 |
| `POST` | `/api|web/v1/maintenance/cases/{caseId}/effect-schedule/resume` | 恢复暂停或失败计划并设置下一次执行 |
| `POST` | `/api|web/v1/maintenance/cases/{caseId}/effect-schedule/execute-now` | 对已到期计划取得租约后立即重校验并执行 |

案件详情的 `effectSchedule` 返回计划状态、租户时区、UTC 下一执行时间、尝试次数、最近尝试及最后错误。`RETROACTIVE`
可在每个冻结保全项均允许且未超过 `maxRetroactiveDays`、未早于保单起期时建案；日期边界按冻结租户时区的自然日
计算，Product 报价使用案件 `specificEffectiveDate`。

```http
POST /api/v1/maintenance/cases/{caseId}/retroactive-impact-analysis
POST /web/v1/maintenance/cases/{caseId}/retroactive-impact-analysis
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
Content-Type: application/json

{
  "operationId": "stable-operation-id"
}
```

追溯影响分析通过 `MaintenanceCaseCommandService` 写门面进入应用编排，Web 不直接依赖 orchestration。编排从 Policy、
Billing、Payment、Claim 正式接口按 `tenantId + policyId + 追溯范围` 取证，并校验远端保单回显；Policy 还必须回显同一
租户。任一权威域缺失、调用失败或回显不一致，分析整体记录为 `FAILED`，不能由操作员补填或忽略。成功结果保存分析
版本、范围、覆盖域、证据版本、结果摘要和独立结构化影响项，案件详情按严重度、归属域和处理状态返回后台清单。

分析状态 `COMPLETED` 只表示四域证据收集完整。正式追溯生效还要求阻断/待处理计数均为零、分析与期间重算证据绑定
一致、Product/Billing 及逐期间检查点完整，关闭期间处理结论通过 Billing 正式查询再次勾稽。Claim 契约当前不提供币种，
因此理赔影响项不推断金额或币种；暂停保单的未来恢复或复效仍需扩展 Policy 建案快照状态门禁。

```http
POST /api/v1/maintenance/cases/{caseId}/retroactive-period-recalculation
POST /web/v1/maintenance/cases/{caseId}/retroactive-period-recalculation
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
Content-Type: application/json

{
  "operationId": "stable-period-operation-id"
}
```

期间重算只接受操作号。Application 从已完成影响分析、Billing 结构化影响项和案件费用检查点派生 Product 请求，保存
Product 检查点后再调用 Billing。Billing 失败时同一操作号重试复用 Product 结果；新操作号生成新版本。案件详情的
`retroactivePeriodRecalculation` 返回原/替代计算摘要、总差额、Billing 批次与每个期间的 before/after、方向、差额、
会计期间和处理状态。`CLOSED_PERIOD_REVIEW` 不改写历史账务，案件进入 `REVIEW_REQUIRED` 并通过以下独立路由处理：

```http
POST /api/v1/maintenance/cases/{caseId}/retroactive-period-resolution
POST /web/v1/maintenance/cases/{caseId}/retroactive-period-resolution
X-Tenant-Id: tenant-001
X-Operator-Id: operator-001
Content-Type: application/json

{
  "operationId": "stable-resolution-operation-id",
  "targetAccountingPeriod": "2026-08",
  "reason": "结转至当前开放期间"
}
```

请求不能提交 Billing 批次、差额、posting reference 或处理结果。服务端状态按 `RESOLVING → COMPLETED/FAILED` 流转，
Billing 为每条关闭期间差额生成 posting reference。案件详情返回处理摘要，并在逐期间行展示目标会计期间、处理状态、
posting reference 和行结果 hash。追溯生效会重新查询 Billing 权威结论并逐行勾稽，然后把完整追溯证据交给 Policy；
Policy 回执必须原样回显该证据，否则案件失败关闭。

### M5-06A 字段冲突刷新与解决

```http
POST /api|web/v1/maintenance/cases/{caseId}/field-conflicts/refresh
POST /api|web/v1/maintenance/cases/{caseId}/field-conflicts/resolve
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
```

刷新请求只接受稳定 `operationId`；解决请求增加 `itemCode/objectId/fieldCode/action/reason`，仅 `REENTER` 还需提交
`dataType/canonicalValue`。动作支持 `USE_CURRENT`、`USE_PROPOSED` 和 `REENTER`。服务端读取 Policy 最新快照，按
`objectId + fieldCode` 对比案件 base/current/proposed；冲突先追加领域事件并将案件置为 `CONFLICTED`，不会继续调用
Policy。全部解决后重建案件 proposed 快照、更新期望 Policy 版本，并按时态恢复 `NOT_STARTED/SCHEDULED`。

生效入口会自动执行版本漂移检查：无冲突时使用最新版本和新 proposed hash 继续；`WAITING_EXTERNAL` 的已冻结请求不
重复刷新。`USE_CURRENT` 形成的无变化字段不会提交 Policy，非状态交易最终没有字段变化时拒绝空交易。详情返回每个
字段的冲突状态、解决动作、理由、操作人和时间；敏感值沿用字段目录与权限脱敏。

### M5-06B 项目撤销与财务补偿

```http
POST /api/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal
POST /web/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
Content-Type: application/json

{
  "operationId": "withdraw-operation-1",
  "reason": "客户取消该项目",
  "paymentMethod": "BANK_CARD"
}
```

`paymentMethod` 仅在撤销原 `CREDIT` 且原退款已经成功、需要重新补收时必填。请求不能提交金额、币种、方向、posting、
冲正或资金结果。首批只允许撤销多项目案件中尚未发起 Policy 生效的项目；唯一剩余项目或已 `EFFECTING/APPLIED` 的项目
必须走案件关闭或新建反向保全。

撤销请求先形成领域事件，再调用 Billing 对原 posting 建立一对一不可变冲正。原 `DEBIT` 收款成功时创建 Payment 独立
退款单，原 `CREDIT` 退款成功时创建确定性补收单；原资金处理中保持 `WAITING_FUNDS`，失败时只冲正 Billing。Billing
成功而 Payment 失败仍保留冲正证据，并可使用同一 `operationId` 和相同请求重试。

全部财务补偿完成后才形成 `COMPLETED`：项目本身及历史费用/字段/任务事件保留，当前字段提案从 proposed 快照移除，
目标项目未完成任务转为 `SKIPPED`。案件详情的项目节点直接返回原入账、原资金终态、冲正、逆向资金、失败原因、操作人
和完成时间，后台无需从日志推断前后状态。

### M5-07 撤销自动恢复与旧执行入口灰度

撤销开始事件已冻结原 `operationId`、请求摘要和原因；撤销编排在调用 Billing/Payment 前追加恢复上下文，补充冻结
支付方式和配置审计信息。后台调度默认每
30 秒扫描一次超过 5 分钟未推进的 `REQUESTED`、`WAITING_FUNDS` 和 `FAILED`，通过 2 分钟数据库租约保证多节点只有
一个节点执行；每批最多 20 条，`FAILED` 达到 5 次尝试后停止自动重试，`REQUESTED/WAITING_FUNDS` 继续等待可判定终态。参数统一位于
`titanium.maintenance.withdrawal-recovery`，可按部署容量调整。

恢复覆盖“外部调用成功、本地结果尚未落库”的进程崩溃窗口，并始终复用原操作号和确定性外部请求。旧 M5-06B 在途案件
若没有 `withdrawalRecoveryConfiguredAt`，不会被自动队列拾取；操作员需使用原载荷人工重试一次，补齐上下文后进入自动
恢复。`WAITING_FUNDS` 只轮询权威资金终态，不提前冲正或推断成功。

旧整案执行入口 `/web/v1/maintenances/{id}/execute` 由以下配置控制：

```yaml
titanium:
  maintenance:
    legacy-execution-enabled: true
```

默认开启以兼容历史调用方。完成生产调用迁移后设置为 `false`，请求会在案件查询和命令发送前失败关闭，不产生新的
`MaintenanceExecutedEvent`；独立案件的正式 Policy 生效入口不受影响。

### 字段草稿 API

```http
PUT /api/v1/maintenance/cases/{caseId}/items/{itemCode}/changes
PUT /web/v1/maintenance/cases/{caseId}/items/{itemCode}/changes
X-Tenant-Id: tenant-id
X-Operator-Id: operator-id
Content-Type: application/json

{
  "proposals": [
    {
      "fieldCode": "policy.holder.mobile",
      "dataType": "TEXT",
      "canonicalValue": "13900000000"
    }
  ]
}
```

Application 先按 `tenantId + caseId` 从初始化完成的独立案件查询上下文解析保单，再重新读取 Policy 当前快照和正式字段目录；聚合继续与案件冻结的 before 快照、项目配置白名单及其他项目草稿交叉校验。成功返回 `204 No Content`，不回显敏感字段原值或拟值。集合字段必须提供稳定 `objectId`，内部快照键为 `objectId:fieldCode`；标量字段保持 `fieldCode`。

请求体只接受 `proposals`，不接受 `policyId` 或其他未声明字段。案件不存在、跨租户或初始化未完成时，不调用 Policy 权威端并统一返回案件不存在，避免调用方选择其他保单或绕过初始化门禁。

### 配置管理 API

统一入口为 `/api/v1/maintenance/configurations`，支持草稿创建/替换/删除、权威校验、送审、审批、驳回、退回草稿、发布、退役、修订、详情、预览、业务时点解析、条件分页和审计历史。所有已有配置的更新都要求 `If-Match`，响应返回 ETag；过期版本返回 412，状态、唯一键和有效期冲突返回 409。

| 方法 | 相对路径 | 用途 |
|---|---|---|
| `POST` | `/` | 创建配置草稿 |
| `PUT/DELETE` | `/{id}` | 按 ETag 替换或删除草稿 |
| `POST` | `/{id}/validate` | 只读权威校验 |
| `POST` | `/{id}/submit`、`approve`、`reject`、`return-to-draft` | 审批流转 |
| `POST` | `/{id}/publish`、`retire`、`revisions` | 发布、退役和修订 |
| `GET` | `/`、`/{id}`、`/{id}/preview` | 分页、详情和只读预览 |
| `GET` | `/effective?itemCode={code}&businessTime={time}` | 按租户、保全项和业务时点解析已发布版本 |
| `GET` | `/{id}/audits` | 脱敏前后快照与字段级差异 |

控制器只依赖 `application.command/query` 入站门面。认证主体、租户、来源 IP 和关联号来自受信请求上下文；权限按动作精确校验。未持有 `maintenance:sensitive:view` 时，字段条件规则和值类型采取保守脱敏，审计接口不透传数据库 JSON。

Policy 字段目录已有正式 Feign Adapter。规则、权限和模板的统一权威只读 API 尚未接入，默认 `UnavailableMaintenanceConfigurationReferenceAdapter` 会失败关闭校验，不会使用本地占位数据放行配置。

部署级灰度开关 `titanium.maintenance.configuration.write-enabled` 默认为 `true`。关闭后禁止创建草稿、创建修订版和首次发布，已有配置查询、预览、审计和业务时点解析保持可用。

### 旧案件兼容的权威保费结算入口

以下 D2-A `/maintenances/{id}` 入口保留原有一体化 Product/Billing 编排，仅服务旧案件兼容链路。新独立
`/maintenance/cases/{caseId}/tasks/{taskId}` 流程使用 `premium-quotes` 和任务级 `premium-settlements` 分别记录报价、
Billing 入账与 Payment 资金证据；两类入口相互独立。

```http
POST /maintenance/web/v1/maintenances/{id}/premium-settlements
X-Tenant-Id: tenant-id
Content-Type: application/json
```

请求必须提供 `originalCalculationId`、产品版本、业务时间、币种、保额、被保人因子、缴费信息、快照、核保调整、渠道、保单年度、原因和操作人。金额不由调用方填写。

服务间契约使用：

```http
POST /maintenance/api/v1/maintenances/{id}/premium-settlements
```

响应返回替代计算 ID、差额 ID/hash、Billing posting ID、方向、金额、币种和当前结算状态。

### 旧人工金额入口灰度切换

旧后台人工金额入口 `POST /maintenance/web/v1/maintenances/{id}/calculate-premium` 由部署属性
`titanium.maintenance.legacy-premium-calculation-enabled` 控制，默认 `true` 保持兼容。关闭后 Application 在读取案件、
调用外部依赖或发送命令前返回 503 `MAINTENANCE_LEGACY_PREMIUM_CALCULATION_DISABLED`。该开关不影响任务级
`premium-settlements`、旧的无金额权威 `premium-settlements` 入口或任何查询。

### 常用查询

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/maintenance/web/v1/maintenances/{id}` | 按租户查询案件详情 |
| `GET` | `/maintenance/web/v1/maintenances/search` | 多条件查询案件 |
| `GET` | `/maintenance/web/v1/maintenances/statistics` | 查询租户级统计 |
| `POST` | `/maintenance/web/v1/maintenances/{id}/execute` | 执行已满足前置条件的案件 |

## 模块结构

```text
titanium-maintenance-api             Feign 契约与 DTO
titanium-maintenance-application     命令入口与跨域编排
titanium-maintenance-domain          聚合、命令、事件、Port
titanium-maintenance-infrastructure  Feign Adapter 与仓储实现
titanium-maintenance-query           CQRS 读模型与投影
titanium-maintenance-web             HTTP Controller 与映射
titanium-maintenance-bootstrap       启动配置与 Liquibase
```

## 构建与测试

```bash
cd titanium-maintenance
mvn clean verify
```

本地 Docker 验收使用 `docker/Dockerfile.maintenance-acceptance` 和 `docker/docker-compose.acceptance-patch.yml`。

## 相关文档

- [设计文档](DESIGN.md)
- [Phase 3 独立建案与查询模型计划](../docs/技术文档/保全域开发实施计划-Phase3.md)
- [Phase 4 流程、审核与收退费计划](../docs/技术文档/保全域开发实施计划-Phase4.md)
- [Phase 4 集成验收报告](../docs/技术文档/保全域Phase4集成验收报告.md)
- [Phase 5 生效与时态交易计划](../docs/技术文档/保全域开发实施计划-Phase5.md)
- [Phase 5 集成验收报告](../docs/技术文档/保全域Phase5集成验收报告.md)
- [模块协作约束](AGENTS.md)
- [模块开发规约](CLAUDE.md)
