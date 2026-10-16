package com.titanium.maintenance.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateLifecycle;

import com.titanium.maintenance.command.ClaimMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.CompleteMaintenanceItemCommand;
import com.titanium.maintenance.command.DecideMaintenanceReviewCommand;
import com.titanium.maintenance.command.DecideMaintenanceUnderwritingCommand;
import com.titanium.maintenance.command.DecideMaintenanceWorkflowConditionCommand;
import com.titanium.maintenance.command.FailMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.FailMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumQuoteCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.RetryMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.StartMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceStatusException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByReviewEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByUnderwritingEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.service.MaintenanceWorkflowPlanner;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;
import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Maintenance 聚合的「流程任务（Workflow）」成员实体（DSN-01c 自聚合根下沉，批 3）。
 * <p>
 * <b>职责边界</b>：承载案件流程任务清单的全生命周期——规划初始化、领取 / 开始 / 完成 / 失败 / 重试、
 * 条件决策、审核决策、核保决策、报价记录、保费结算记录与结算失败恢复、项目收口——以及工作流幂等
 * 哈希账本（operationId → payloadHash，见 {@link #workflowOperationHashes}）。任务状态载体为不可变
 * 记录 {@link MaintenanceWorkflowTask}，本实体持有最新清单，命令处理器校验后 apply 事件、由本实体的
 * {@code @EventSourcingHandler} 接管状态重建。
 * </p>
 * <p>
 * <b>成员不互引（🔴 勿破坏）</b>：本实体只持有自身主题的状态，不引用聚合内其它成员；快照序列化
 * （Axon JacksonSerializer）会把成员内联进聚合 JSON，成员互引会导致同一对象被序列化两次、反序列化出
 * 相互独立的副本 ⇒ 状态不同步。依赖其它主题的判定留在聚合根（如完成信息录入须校验实际字段变更，
 * 读保全项状态；凭证模板须取保全项冻结配置）。
 * </p>
 * <p>
 * <b>镜像上下文（由聚合根同步，🔴 新增变更点时须同批维护）</b>：{@code status} 在案件状态变更的
 * 聚合根源码五处（状态变更事件 / 执行完成 / 两处审核拒绝 / 生效状态转为已生效）经
 * {@link #markStatus(MaintenanceStatus)} 同步；{@code initializationCompleted} 在案件初始化完成事件处
 * 经 {@link #markInitialized()} 同步——流程可推进性判定（{@link #requireWorkflowMutable}）依赖二者；
 * {@code createdBy}（审核职责分离判定 {@link #requireSeparatedReviewer} 依赖）、{@code tenantId}、
 * {@code maintenanceId} 在创建时注入后不变。
 * </p>
 */
@Getter
@NoArgsConstructor
public class MaintenanceWorkflow {

    /** 租户ID（创建时注入，命令归属校验用） */
    private String                                tenantId;

    /** 聚合标识（创建时注入，事件发布与异常构造用） */
    private MaintenanceId                         maintenanceId;

    /** 案件状态镜像（聚合根在状态变更各点同步，流程可推进性判定用） */
    private MaintenanceStatus                     status;

    /** 案件初始化完成镜像（聚合根在初始化完成事件处同步，流程可推进性判定用） */
    private boolean                               initializationCompleted;

    /** 建案人（创建时注入，审核职责分离判定用；创建后不变） */
    private String                                createdBy;

    /** 流程任务清单（规划初始化后由事件重建） */
    @Getter(AccessLevel.NONE)
    private List<MaintenanceWorkflowTask>         workflowTasks;

    /**
     * 工作流幂等哈希账本（operationId → payloadHash）。
     * <p>🔴 进序列化（DSN-01c 批 4 评估后的处置）：原实现刻意不进序列化（无 getter），但账本由事件回放
     * 重建，快照恢复时被快照覆盖的事件不再重放 ⇒ 恢复后账本条目丢失 ⇒ 同一 operationId 重试将绕过
     * 幂等（重复推进任务）。与聚合根 fieldConflictOperationHashes 同族、同批修复。Lombok 生成由
     * {@code @Getter(AccessLevel.NONE)} 抑制，{@link #getWorkflowOperationHashes()} 手写提供只读拷贝
     * （与任务清单同法）。</p>
     */
    @Getter(AccessLevel.NONE)
    private Map<String, String>                   workflowOperationHashes;

    MaintenanceWorkflow(String tenantId, MaintenanceId maintenanceId, MaintenanceStatus status,
            boolean initializationCompleted, String createdBy) {
        this.tenantId = tenantId;
        this.maintenanceId = maintenanceId;
        this.status = status;
        this.initializationCompleted = initializationCompleted;
        this.createdBy = createdBy;
        this.workflowTasks = new ArrayList<>();
        this.workflowOperationHashes = new HashMap<>();
    }

    /** 案件状态变更时由聚合根同步（流程可推进性判定依赖案件终态）。 */
    void markStatus(MaintenanceStatus next) {
        this.status = next;
    }

    /** 案件初始化完成时由聚合根同步（流程可推进性判定依赖初始化完成）。 */
    void markInitialized() {
        this.initializationCompleted = true;
    }

    /**
     * 规划并初始化案件的流程任务清单（幂等：已有任务时直接返回）。
     * <p>保全项清单状态属案件级、归属聚合根，由聚合根传入；本方法只负责规划与发布初始化事件，
     * 任务状态重建由本实体的 {@code on(MaintenanceWorkflowInitializedEvent)} 接管。</p>
     */
    void initializeWorkflow(MaintenanceId maintenanceId, List<MaintenanceItemInstance> itemInstances,
            String initializedBy) {
        if (workflowTasks != null && !workflowTasks.isEmpty()) {
            return;
        }
        List<MaintenanceWorkflowTask> tasks = new MaintenanceWorkflowPlanner().plan(maintenanceId, itemInstances);
        AggregateLifecycle.apply(
                new MaintenanceWorkflowInitializedEvent(maintenanceId, tasks, LocalDateTime.now(), initializedBy,
                        tenantId));
    }

    // 处理领取流程任务命令
    /** 领取可处理任务。 */
    @CommandHandler
    public void handle(ClaimMaintenanceWorkflowTaskCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.CLAIM, command.taskId(), null, null, null, null, command.operatorId());
        transition(command.taskId(), operation, task -> {
            requireSeparatedReviewer(task, command.operatorId());
            return task.claim(operation);
        }, false);
    }

    /** 开始处理已领取任务。 */
    @CommandHandler
    public void handle(StartMaintenanceWorkflowTaskCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.START, command.taskId(), null, null, null, null, command.operatorId());
        transition(command.taskId(), operation, task -> task.start(operation), false);
    }

    /** 将处理中任务记为失败。 */
    @CommandHandler
    public void handle(FailMaintenanceWorkflowTaskCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.FAIL, command.taskId(), null, null, command.failureCode(),
                command.failureReason(), command.operatorId());
        transition(command.taskId(), operation, task -> task.fail(operation), false);
    }

    /** 将失败任务恢复为可领取状态。 */
    @CommandHandler
    public void handle(RetryMaintenanceWorkflowTaskCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.RETRY, command.taskId(), null, null, null, command.reason(),
                command.operatorId());
        transition(command.taskId(), operation, task -> task.retry(operation), false);
    }

    /** 记录条件规则结论。 */
    @CommandHandler
    public void handle(DecideMaintenanceWorkflowConditionCommand command) {
        requireSameTenant(command.tenantId());
        String resultCode = command.decision() == null ? null : command.decision().getCode();
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.DECIDE_CONDITION, command.taskId(), command.ruleVersion(),
                command.inputHash(), resultCode, command.reason(), command.operatorId());
        boolean activateNext = command.decision() == MaintenanceWorkflowConditionDecision.SKIP;
        transition(command.taskId(), operation, task -> task.decideCondition(command.decision(), operation),
                activateNext);
    }

    /** 使用人工或自动审核专用证据决定审核任务。 */
    @CommandHandler
    public void handle(DecideMaintenanceReviewCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowReviewEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("DecideMaintenanceReviewCommand", "evidence", "审核证据不能为空");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.DECIDE_REVIEW, command.taskId(), evidence.policyVersion(),
                evidence.contentHash(), evidence.decision().getCode(), evidence.comment(), command.operatorId());
        boolean approved = evidence.decision() == MaintenanceReviewDecision.APPROVE;
        boolean applied = transition(command.taskId(), operation, task -> {
            if (evidence.mode() == MaintenanceReviewMode.MANUAL) {
                requireSeparatedReviewer(task, command.operatorId());
            }
            return task.decideReview(evidence, operation);
        }, approved);
        if (applied && !approved) {
            AggregateLifecycle.apply(new MaintenanceCaseRejectedByReviewEvent(maintenanceId, command.taskId(),
                    evidence.contentHash(), evidence.policyCode(), evidence.policyVersion(), evidence.comment(),
                    operation.operatedAt(), command.operatorId(), tenantId));
        }
    }

    /** 使用 Underwriting 权威证据决定核保任务，不接受调用方自报结论。 */
    @CommandHandler
    public void handle(DecideMaintenanceUnderwritingCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceUnderwritingEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("DecideMaintenanceUnderwritingCommand", "evidence", "核保证据不能为空");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.DECIDE_UNDERWRITING, command.taskId(), evidence.ruleVersion(),
                evidence.contentHash(), evidence.conclusion().getCode(), evidence.summary(), command.operatorId());
        boolean accepted = evidence.conclusion().accepted()
                && evidence.conclusion() != MaintenanceUnderwritingConclusion.NOT_REQUIRED;
        boolean applied = transition(command.taskId(), operation, task -> task.decideUnderwriting(evidence, operation),
                accepted);
        if (applied && evidence.conclusion() == MaintenanceUnderwritingConclusion.REJECTED) {
            AggregateLifecycle.apply(
                    new MaintenanceCaseRejectedByUnderwritingEvent(maintenanceId, command.taskId(),
                            evidence.underwritingCaseId(), evidence.contentHash(), evidence.ruleVersion(),
                            evidence.modelVersion(), evidence.summary(), operation.operatedAt(),
                            command.operatorId(), tenantId));
        }
    }

    /** 使用 Product 权威报价或配置无需报价结论更新费用任务，不提前激活生效步骤。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumQuoteCommand command) {
        requireSameTenant(command.tenantId());
        MaintenancePremiumQuoteEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumQuoteCommand", "evidence", "报价证据不能为空");
        }
        if (premiumQuoteOperationAlreadyApplied(command, evidence)) {
            return;
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE, command.taskId(), evidence.evidenceVersion(),
                evidence.contentHash(), evidence.status().getCode(), evidence.detailSummary(), command.operatorId());
        transition(command.taskId(), operation, task -> task.recordPremiumQuote(evidence, operation), false);
    }

    /** 记录 Billing 与 Payment 双重门禁，资金成功后才激活后继生效任务。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumSettlementCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceBillingPostingEvidence posting = command.postingEvidence();
        MaintenanceFundSettlementEvidence funds = command.fundEvidence();
        if (posting == null || funds == null) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumSettlementCommand", "evidence",
                    "入账和资金证据不能为空");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.SETTLE_PREMIUM, command.taskId(), funds.evidenceVersion(posting),
                funds.gateContentHash(posting),
                posting.status() == MaintenanceBillingPostingStatus.REVERSED ? posting.status().getCode()
                        : funds.status().getCode(),
                funds.detailSummary(), command.operatorId());
        transition(command.taskId(), operation, task -> task.settlePremium(posting, funds, operation),
                posting.status() == MaintenanceBillingPostingStatus.POSTED && funds.status().completed());
    }

    /** 外部结算调用失败时记录可恢复失败，不激活后继任务。 */
    @CommandHandler
    public void handle(FailMaintenancePremiumSettlementCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.FAIL_PREMIUM_SETTLEMENT, command.taskId(), null, null, command.failureCode(),
                command.failureReason(), command.operatorId());
        transition(command.taskId(), operation, task -> task.failPremiumSettlement(operation), false);
    }

    /** 完成案件终结标记步骤；同项目前序任务须全部形成终态。 */
    @CommandHandler
    public void handle(CompleteMaintenanceItemCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.COMPLETE_ITEM, command.taskId(), null, null,
                MaintenanceWorkflowTaskStatus.COMPLETED.getCode(), null, command.operatorId());
        transition(command.taskId(), operation, task -> {
            requirePredecessorsTerminal(task);
            return task.completeItem(operation);
        }, false);
    }

    /** 任务清单初始化回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceWorkflowInitializedEvent event) {
        this.workflowTasks = new ArrayList<>(event.tasks());
        if (this.workflowOperationHashes == null) {
            this.workflowOperationHashes = new HashMap<>();
        }
    }

    /** 任务迁移回放：业务状态重建。审计字段由聚合根侧同名处理器维护（双监听为设计意图）。 */
    @EventSourcingHandler
    public void on(MaintenanceWorkflowTaskTransitionedEvent event) {
        replaceWorkflowTask(event.afterTask());
        if (event.activatedTaskAfter() != null) {
            replaceWorkflowTask(event.activatedTaskAfter());
        }
        if (workflowOperationHashes == null) {
            workflowOperationHashes = new HashMap<>();
        }
        workflowOperationHashes.put(event.operationId(), event.operationHash());
    }

    /**
     * 取非跳过 EFFECT 任务标识（升序）。聚合根在执行调度、字段冲突恢复、案件级生效等主题交叉处调用。
     */
    List<String> activeEffectTaskIds() {
        return workflowTasks == null ? List.of()
                : workflowTasks.stream()
                        .filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                        .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED)
                        .map(MaintenanceWorkflowTask::taskId)
                        .sorted()
                        .toList();
    }

    /** 重试全部失败 EFFECT 任务（聚合根在调度恢复路径调用）。 */
    void retryFailedEffectTasks(String operationId, String reason, String operatorId) {
        if (operationId == null || operationId.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "operationId", "恢复操作号不能为空");
        }
        List<MaintenanceWorkflowTask> failedTasks = workflowTasks.stream()
                .filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                .filter(task -> task.status() == MaintenanceWorkflowTaskStatus.FAILED)
                .toList();
        for (MaintenanceWorkflowTask task : failedTasks) {
            MaintenanceWorkflowOperation operation = workflowOperation(
                    caseTaskOperationId(operationId, task.taskId()), MaintenanceWorkflowAction.RETRY,
                    task.taskId(), null, null, null, reason, operatorId);
            transition(task.taskId(), operation, candidate -> candidate.retry(operation), false);
        }
    }

    /** 是否全部 EFFECT 任务已形成终态（完成或跳过）且非空。 */
    boolean allEffectTasksApplied() {
        List<MaintenanceWorkflowTask> effectTasks = workflowTasks.stream()
                .filter(task -> task.stepType() == MaintenanceStepType.EFFECT).toList();
        return !effectTasks.isEmpty()
                && effectTasks.stream().allMatch(task -> task.status() == MaintenanceWorkflowTaskStatus.COMPLETED
                        || task.status() == MaintenanceWorkflowTaskStatus.SKIPPED);
    }

    /** 校准案件级生效任务清单须覆盖全部非跳过 EFFECT 任务，返回归一后的任务标识。 */
    List<String> requireAllEffectTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()
                || taskIds.stream().anyMatch(taskId -> taskId == null || taskId.isBlank())
                || new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new MaintenanceValidationException("MaintenanceCaseEffect", "taskIds", "案件级生效任务不能为空、空白或重复");
        }
        List<String> expected = workflowTasks == null ? List.of()
                : workflowTasks.stream().filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                        .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED)
                        .map(MaintenanceWorkflowTask::taskId).sorted().toList();
        List<String> actual = taskIds.stream().map(String::trim).sorted().toList();
        if (!expected.equals(actual)) {
            throw new MaintenanceValidationException("MaintenanceCaseEffect", "taskIds", "案件级生效必须覆盖全部非跳过 EFFECT 任务");
        }
        return actual;
    }

    /** 校验补偿事实关联的 EFFECT 任务上下文（全部非跳过任务共享同一 Policy 请求事实）。 */
    void requireEffectCompensationContext(String taskId, MaintenanceEffectCompensationEvidence evidence) {
        List<MaintenanceWorkflowTask> effectTasks = workflowTasks == null ? List.of()
                : workflowTasks.stream().filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                        .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED).toList();
        requireAllEffectTaskIds(effectTasks.stream().map(MaintenanceWorkflowTask::taskId).toList());
        if (taskId == null || taskId.isBlank()
                || effectTasks.stream().noneMatch(task -> task.taskId().equals(taskId.trim()))) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "taskId",
                    "补偿事实必须关联案件内的非跳过 EFFECT 任务");
        }
        if (effectTasks.stream().anyMatch(task -> task.status() != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                || task.effectEvidence() == null || task.effectEvidence().request() == null)) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "effectTasks",
                    "案件全部非跳过 EFFECT 任务必须等待 Policy 回执");
        }
        MaintenanceEffectRequestEvidence request = effectTasks.getFirst().effectEvidence().request();
        if (effectTasks.stream().anyMatch(task -> !request.equals(task.effectEvidence().request()))) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "effectTasks",
                    "案件全部非跳过 EFFECT 任务必须共享同一 Policy 请求事实");
        }
        if (!request.requestId().equals(evidence.requestId())) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "evidence",
                    "补偿事实必须关联当前冻结的 Policy 请求");
        }
    }

    /** 案件级操作号按任务派生（聚合根在案件级 effect 处理器中经 workflow.caseTaskOperationId 调用）。 */
    String caseTaskOperationId(String operationId, String taskId) {
        if (operationId == null || operationId.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceCaseEffect", "operationId", "案件级生效操作号不能为空");
        }
        return operationId.trim() + ":" + taskId;
    }

    /** 任务迁移统一出口（聚合根在案件级 effect 处理器中同样经此迁移，勿绕过幂等与可推进校验）。 */
    boolean transition(String taskId, MaintenanceWorkflowOperation operation,
                       UnaryOperator<MaintenanceWorkflowTask> transition, boolean activateNext) {
        if (workflowOperationAlreadyApplied(operation)) {
            return false;
        }
        MaintenanceWorkflowTask beforeTask = findWorkflowTask(taskId);
        requireWorkflowMutable(beforeTask, operation.action().getCode());
        MaintenanceWorkflowTask afterTask = transition.apply(beforeTask);
        MaintenanceWorkflowTask activatedBefore = null;
        MaintenanceWorkflowTask activatedAfter = null;
        if (activateNext) {
            activatedBefore = findNextPendingTask(beforeTask);
            if (activatedBefore != null) {
                activatedAfter = activatedBefore.activate(operation);
            }
        }
        AggregateLifecycle.apply(new MaintenanceWorkflowTaskTransitionedEvent(maintenanceId, beforeTask, afterTask,
                activatedBefore, activatedAfter, operation.operationId(), operation.payloadHash(),
                operation.operatedAt(), operation.operatedBy(), tenantId));
        return true;
    }

    /** 构造工作流操作载荷（聚合根在案件级 effect 处理器中同样经此构造）。 */
    MaintenanceWorkflowOperation workflowOperation(String operationId, MaintenanceWorkflowAction action,
                                                   String taskId, String evidenceVersion, String evidenceHash,
                                                   String resultCode, String reason, String operatorId) {
        return MaintenanceWorkflowOperation.create(operationId, action, taskId, evidenceVersion, evidenceHash,
                resultCode, reason, LocalDateTime.now(), operatorId);
    }

    private boolean premiumQuoteOperationAlreadyApplied(
            RecordMaintenancePremiumQuoteCommand command,
            MaintenancePremiumQuoteEvidence evidence) {
        if (workflowOperationHashes == null) {
            workflowOperationHashes = new HashMap<>();
        }
        String existingHash = workflowOperationHashes.get(command.operationId());
        if (existingHash == null) {
            return false;
        }
        MaintenancePremiumQuoteEvidence existingEvidence =
                findWorkflowTask(command.taskId()).premiumQuoteEvidence();
        if (existingEvidence == null || !existingEvidence.sameIdempotencyFact(evidence)) {
            return false;
        }
        MaintenanceWorkflowOperation existingOperation = workflowOperation(
                command.operationId(), MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                command.taskId(), existingEvidence.evidenceVersion(), existingEvidence.contentHash(),
                existingEvidence.status().getCode(), existingEvidence.detailSummary(), command.operatorId());
        return existingHash.equals(existingOperation.payloadHash());
    }

    private boolean workflowOperationAlreadyApplied(MaintenanceWorkflowOperation operation) {
        if (workflowOperationHashes == null) {
            workflowOperationHashes = new HashMap<>();
        }
        String existingHash = workflowOperationHashes.get(operation.operationId());
        if (existingHash == null) {
            return false;
        }
        if (existingHash.equals(operation.payloadHash())) {
            return true;
        }
        throw new MaintenanceValidationException("MaintenanceWorkflowOperation", "operationId", "同一操作号不能提交不同载荷");
    }

    /**
     * 校验案件与流程任务可推进。
     * <p>
     * 收口步骤（{@link MaintenanceStepType#closingPhase()}：出具凭证、完成）发生在案件生效完成之后，**案件级冻结
     * 不适用于它们**——否则 {@code EFFECT → DOCUMENT → COMPLETE} 模板下收口步骤在结构上不可达（案件于生效回执
     * 即置 {@code COMPLETED}）。其余步骤沿用原守卫，含收口步骤在案件被拒绝时，避免驳回后仍能改动保单内容。
     * </p>
     * <p>
     * 案件状态经镜像字段判定（聚合根在五个状态变更点同步，见类注释）；镜像判据与其在聚合根上的原判据
     * （itemStatusMutable）逐字一致——保全案件只有待处理或处理中才可修改保全项。
     * </p>
     *
     * @param task   待推进任务（其步骤类型决定是否豁免案件级冻结）
     * @param target 目标动作编码，仅用于异常文案定位
     */
    private void requireWorkflowMutable(MaintenanceWorkflowTask task, String target) {
        if (status != MaintenanceStatus.COMPLETED || !task.stepType().closingPhase()) {
            if (status != MaintenanceStatus.PENDING && status != MaintenanceStatus.PROCESSING) {
                throw new MaintenanceStatusException(maintenanceId.id(), status.name(), target,
                        "只有待处理或处理中的保全案件可以修改保全项");
            }
        }
        if (!initializationCompleted || workflowTasks == null || workflowTasks.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceWorkflowTask", "workflowTasks", "案件流程任务尚未初始化");
        }
    }

    /**
     * 校验同项目全部前序任务已形成终态（已完成或已跳过）。
     * <p>
     * 前驱顺序是**项目内**概念（同一 {@code itemCode} 的步骤序），故只在同项目任务间比较，不涉及其它保全项。
     * </p>
     */
    private void requirePredecessorsTerminal(MaintenanceWorkflowTask task) {
        List<String> unfinished = workflowTasks.stream()
                .filter(candidate -> candidate.itemCode().equals(task.itemCode()))
                .filter(candidate -> candidate.sequence() < task.sequence())
                .filter(candidate -> candidate.status() != MaintenanceWorkflowTaskStatus.COMPLETED
                        && candidate.status() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .map(MaintenanceWorkflowTask::taskId)
                .toList();
        if (!unfinished.isEmpty()) {
            throw new MaintenanceConflictException("MaintenanceWorkflowTask", "status",
                    "前序任务未形成终态，不能完成项目: " + String.join(",", unfinished));
        }
    }

    /** 按任务标识查找流程任务（聚合根在凭证出具、任务完成等留驻处理器中调用）。 */
    MaintenanceWorkflowTask findWorkflowTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceWorkflowTask", "taskId", "任务标识不能为空");
        }
        return workflowTasks.stream().filter(task -> task.taskId().equals(taskId.trim())).findFirst().orElseThrow(
                () -> new MaintenanceValidationException("MaintenanceWorkflowTask", "taskId", "案件中不存在流程任务"));
    }

    private void requireSeparatedReviewer(MaintenanceWorkflowTask task, String reviewerId) {
        if (task.stepType() != MaintenanceStepType.REVIEW) {
            return;
        }
        if (createdBy == null || createdBy.isBlank() || reviewerId == null || reviewerId.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "operatorId", "无法证明建案人与审核人职责分离");
        }
        if (createdBy.equals(reviewerId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowTask", "operatorId", "建案人不能领取或决定审核任务");
        }
    }

    private MaintenanceWorkflowTask findNextPendingTask(MaintenanceWorkflowTask completedTask) {
        return workflowTasks.stream().filter(task -> task.itemCode().equals(completedTask.itemCode()))
                .filter(task -> task.sequence() > completedTask.sequence())
                .filter(task -> task.status() == MaintenanceWorkflowTaskStatus.PENDING)
                .min((left, right) -> Integer.compare(left.sequence(), right.sequence())).orElse(null);
    }

    private void replaceWorkflowTask(MaintenanceWorkflowTask replacement) {
        for (int index = 0; index < workflowTasks.size(); index++) {
            if (workflowTasks.get(index).taskId().equals(replacement.taskId())) {
                workflowTasks.set(index, replacement);
                return;
            }
        }
        throw new MaintenanceValidationException("MaintenanceWorkflowTaskTransitionedEvent", "taskId", "事件引用了不存在的流程任务");
    }

    /** 按保全项过滤流程任务（聚合根在项目撤销判定中调用）。 */
    List<MaintenanceWorkflowTask> itemTasks(String itemCode) {
        return workflowTasks == null ? List.of() : workflowTasks.stream()
                .filter(task -> task.itemCode().equals(itemCode))
                .toList();
    }

    /** 流程任务清单（只读视图；未初始化时为空表）。 */
    public List<MaintenanceWorkflowTask> getWorkflowTasks() {
        return workflowTasks == null ? List.of() : List.copyOf(workflowTasks);
    }

    /**
     * 工作流幂等账本只读视图（未初始化时为空表）。
     * <p>🔴 此 getter 不可删（DSN-01c 批 4 评估修复）：账本必须随快照序列化往返存活——Axon 恢复聚合时
     * 被快照覆盖的事件不再重放，无 getter 的字段不进快照 ⇒ 恢复后账本条目整体丢失 ⇒ 同一 operationId
     * 重试绕过幂等。详见 {@link #workflowOperationHashes} 字段注释。</p>
     */
    public Map<String, String> getWorkflowOperationHashes() {
        return workflowOperationHashes == null ? Map.of() : Map.copyOf(workflowOperationHashes);
    }

    /**
     * 校验命令携带的租户与案件归属租户一致（聚合根守卫的成员副本，规则逐字同源）。
     * <p>
     * 租户缺失、空白或不符一律以「保全不存在」拒绝——不区分「不存在」与「不属于本租户」，避免跨租户探测。
     * 成员实体的处理器同样直接处理外部命令（Axon 按 {@code @AggregateMember} 自动路由），故同受守护约束。
     * </p>
     *
     * @param commandTenantId 命令携带的租户ID
     */
    private void requireSameTenant(String commandTenantId) {
        if (commandTenantId == null || commandTenantId.isBlank() || !Objects.equals(commandTenantId, tenantId)) {
            throw new MaintenanceNotFoundException();
        }
    }
}
