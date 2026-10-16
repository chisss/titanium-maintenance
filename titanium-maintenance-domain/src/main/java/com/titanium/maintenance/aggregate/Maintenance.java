package com.titanium.maintenance.aggregate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.common.domain.BaseAggregate;
import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.AddMaintenanceItemCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.command.CompleteMaintenanceCaseInitializationCommand;
import com.titanium.maintenance.command.CompleteMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.ConfigureMaintenanceItemWithdrawalRecoveryCommand;
import com.titanium.maintenance.command.CreateMaintenanceCaseCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.FailMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseInvestmentSwitchCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanDisbursementCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanRepaymentCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyReissueCommand;
import com.titanium.maintenance.command.RecordMaintenanceDocumentCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleFailureCommand;
import com.titanium.maintenance.command.RecordMaintenanceItemWithdrawalCompensationCommand;
import com.titanium.maintenance.command.RefreshMaintenanceFieldConflictsCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.ResolveMaintenanceFieldConflictCommand;
import com.titanium.maintenance.command.ResumeMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.command.StartMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceStatusException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseItemsPlannedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByReviewEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByUnderwritingEvent;
import com.titanium.maintenance.event.MaintenanceChangeAddedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationRequiredEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationResolvedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleAttemptedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleCompletedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleFailedEvent;
import com.titanium.maintenance.event.MaintenanceEffectSchedulePausedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleResumedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduledEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceEndorsementIssuedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenanceFieldChangesRecordedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictResolvedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictsRefreshedEvent;
import com.titanium.maintenance.event.MaintenanceFinancialSettlementRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalCompensationRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalFailedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalRecoveryConfiguredEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalStartedEvent;
import com.titanium.maintenance.event.MaintenancePolicySnapshotCapturedEvent;
import com.titanium.maintenance.event.MaintenancePremiumAdjustmentRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenancePremiumPostingRecordedEvent;
import com.titanium.maintenance.event.MaintenanceProposedSnapshotRecordedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveProductRecalculationRecordedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.service.MaintenanceFieldConflictPlanner;
import com.titanium.maintenance.service.MaintenanceFieldProposalPlanner;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceChange;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.MaintenanceSettlementRefs;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.SurrenderPolicySnapshot;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposalPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotSet;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalRecoveryContext;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceDocumentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.maintenance.valueobject.workflow.MaintenanceInvestmentSwitchEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanDisbursementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanRepaymentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyReissueEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.valueobject.Money;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Aggregate
/** 仅供 Axon 回放使用（Lombok 生成 protected 无参构造器），业务代码禁止调用 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Maintenance extends BaseAggregate {
    @AggregateIdentifier
    private MaintenanceId                                id;
    private PolicyId                                     policyId;
    private CustomerId                                   customerId;
    private MaintenanceType                              maintenanceType;
    private MaintenanceStatus                            status;
    private EffectiveTimeType                            effectiveTimeType;
    private LocalDateTime                                specificEffectiveDate;
    @AggregateMember
    private MaintenanceRetroactiveResolution             retroactiveResolution;
    @AggregateMember
    private MaintenanceSettlement                        settlement;
    private String                                       description;
    private List<MaintenanceChange>                      changes;
    private List<MaintenanceItemInstance>                itemInstances;
    private Map<String, MaintenanceItemWithdrawal>       itemWithdrawals;
    private Map<String, MaintenanceItemWithdrawalRecoveryContext> itemWithdrawalRecoveryContexts;
    private List<String>                                 plannedItemCodes;
    private boolean                                      initializationCompleted;
    private MaintenanceChannel                           source;
    private String                                       clientRequestKey;
    private String                                       creationRequestFingerprint;
    private PolicyMaintenanceSnapshot                    policySnapshot;
    private MaintenanceSnapshotSet                       snapshotSet;
    private Map<String, MaintenanceFieldValue>           proposedFieldValues;
    private Map<String, MaintenanceFieldCatalogSnapshot> fieldCatalogSnapshots;
    private Map<String, String>                          fieldConflictOperationHashes;
    @AggregateMember
    private MaintenanceWorkflow                          workflow;
    @AggregateMember
    private MaintenanceEffectPlan                        effectPlan;
    private String                                       createdBy;
    private String                                       updatedBy;

    // 创建保全记录命令处理器
    @CommandHandler
    public Maintenance(CreateMaintenanceCommand command) {
        AggregateLifecycle.apply(new MaintenanceCreatedEvent(command.id(), command.policyId(), command.customerId(),
                command.maintenanceType(), command.effectiveTimeType(), command.specificEffectiveDate(),
                command.description(), LocalDateTime.now(), command.createdBy(), command.tenantId()));
    }

    /** 独立入口建案；相同幂等键重试由同一聚合校验请求载荷。 */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(CreateMaintenanceCaseCommand command) {
        if (this.id != null) {
            requireSameTenant(command.tenantId());
            assertSameCreationRequest(command);
            LocalDateTime retriedAt = LocalDateTime.now();
            capturePolicySnapshot(command, retriedAt);
            planItems(command, retriedAt);
            return;
        }
        LocalDateTime openedAt = LocalDateTime.now();
        AggregateLifecycle.apply(new MaintenanceCreatedEvent(command.id(), command.policyId(), command.customerId(),
                command.primaryMaintenanceType(), command.effectiveTimeType(), command.specificEffectiveDate(),
                command.description(), openedAt, command.createdBy(), command.tenantId()));
        AggregateLifecycle.apply(new MaintenanceCaseOpenedEvent(command.id(), command.idempotencyKey().source(),
                command.idempotencyKey().clientRequestKey(), command.requestFingerprint(), openedAt,
                command.createdBy(), command.tenantId()));
        capturePolicySnapshot(command, openedAt);
        planItems(command, openedAt);
    }

    // 处理创建事件
    @EventSourcingHandler
    public void on(MaintenanceCreatedEvent event) {
        this.id = event.maintenanceId();
        this.policyId = event.policyId();
        this.customerId = event.customerId();
        this.maintenanceType = event.maintenanceType();
        this.status = MaintenanceStatus.PENDING;
        this.effectiveTimeType = event.effectiveTimeType();
        this.specificEffectiveDate = event.specificEffectiveDate();
        this.description = event.description();
        this.changes = new ArrayList<>();
        this.itemInstances = new ArrayList<>();
        this.itemWithdrawals = new HashMap<>();
        this.itemWithdrawalRecoveryContexts = new HashMap<>();
        this.plannedItemCodes = new ArrayList<>();
        this.initializationCompleted = false;
        this.proposedFieldValues = new TreeMap<>();
        this.fieldCatalogSnapshots = new HashMap<>();
        this.fieldConflictOperationHashes = new HashMap<>();
        this.createTime = event.createdAt();
        this.createdBy = event.createdBy();
        this.updateTime = event.createdAt();
        this.updatedBy = event.createdBy();
        this.tenantId = event.tenantId();
        // 追溯处置成员实体（DSN-01c 下沉）：创建时注入静态上下文，业务状态由成员自身事件溯源处理器重建
        this.retroactiveResolution = new MaintenanceRetroactiveResolution(event.tenantId(), event.maintenanceId(),
                event.effectiveTimeType(), event.specificEffectiveDate());
        // 费用结算成员实体（DSN-01c 下沉）：创建时注入静态上下文与初始状态，业务状态由成员自身事件溯源处理器重建
        this.settlement = new MaintenanceSettlement(event.tenantId(), event.maintenanceId(),
                event.maintenanceType(), MaintenanceStatus.PENDING);
        // 流程任务成员实体（DSN-01c 下沉）：创建时注入静态上下文与初始状态（建案人供审核职责分离判定），业务状态由成员自身事件溯源处理器重建
        this.workflow = new MaintenanceWorkflow(event.tenantId(), event.maintenanceId(), this.status,
                this.initializationCompleted, this.createdBy);
        // 生效计划成员实体（DSN-01c 批 4 下沉）：创建时注入静态上下文，业务状态由成员自身事件溯源处理器重建
        this.effectPlan = new MaintenanceEffectPlan(event.tenantId(), event.maintenanceId());
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseOpenedEvent event) {
        this.source = event.source();
        this.clientRequestKey = event.clientRequestKey();
        this.creationRequestFingerprint = event.requestFingerprint();
        this.updateTime = event.openedAt();
        this.updatedBy = event.openedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseItemsPlannedEvent event) {
        this.plannedItemCodes = new ArrayList<>(event.itemCodes());
        this.updateTime = event.plannedAt();
        this.updatedBy = event.plannedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseInitializationCompletedEvent event) {
        this.initializationCompleted = true;
        if (retroactiveResolution != null) {
            retroactiveResolution.markInitialized();
        }
        if (workflow != null) {
            workflow.markInitialized();
        }
        this.updateTime = event.completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenancePolicySnapshotCapturedEvent event) {
        this.policySnapshot = event.snapshot();
        this.snapshotSet = MaintenanceSnapshotSet.capturedBefore(event.snapshot().beforeSnapshot());
        this.proposedFieldValues = new TreeMap<>(event.snapshot().fieldValues());
        this.updateTime = event.recordedAt();
        this.updatedBy = event.recordedBy();
    }

    // 处理变更状态命令
    @CommandHandler
    public void handle(ChangeMaintenanceStatusCommand command) {
        requireSameTenant(command.tenantId());
        if (this.status == MaintenanceStatus.COMPLETED || this.status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceStatusException(this.id.id(), this.status.name(), command.newStatus().name(),
                    "已完成或已拒绝的保全不允许变更状态");
        }
        if (this.status == command.newStatus()) {
            throw new MaintenanceStatusException(this.id.id(), this.status.name(), command.newStatus().name(),
                    "保全已处于该状态");
        }

        AggregateLifecycle.apply(new MaintenanceStatusChangedEvent(command.id(), this.status, command.newStatus(),
                command.changeReason(), LocalDateTime.now(), command.changedBy(), this.tenantId));
    }

    // 处理添加变更记录命令
    @CommandHandler
    public void handle(AddMaintenanceChangeCommand command) {
        requireSameTenant(command.tenantId());
        AggregateLifecycle.apply(new MaintenanceChangeAddedEvent(command.id(), command.changeType(),
                command.fieldName(), command.oldValue(), command.newValue(), LocalDateTime.now(), command.createdBy(),
                this.tenantId));
    }

    /** 向草稿案件添加配置版本已冻结的保全项。 */
    @CommandHandler
    public void handle(AddMaintenanceItemCommand command) {
        requireSameTenant(command.tenantId());
        requireItemStatusMutable("ADD_MAINTENANCE_ITEM");
        LocalDateTime addedAt = LocalDateTime.now();
        MaintenanceItemSelectionEvidence evidence = command.selectionEvidence();
        MaintenanceItemInstance item = evidence == null ? MaintenanceItemInstance.from(command.definition(), addedAt)
                : MaintenanceItemInstance.from(command.definition(), evidence, addedAt);
        MaintenanceItemInstance existingItem = itemInstances.stream()
                .filter(existing -> existing.itemCode().equals(item.itemCode())).findFirst().orElse(null);
        if (existingItem != null) {
            if (existingItem.sameFrozenSelection(command.definition(), item.selectionEvidence())) {
                return;
            }
            throw new MaintenanceValidationException("AddMaintenanceItemCommand", "definition",
                    "同一案件不能绑定不同的同编码保全项: " + item.itemCode());
        }
        if (source != null && initializationCompleted) {
            throw new MaintenanceValidationException("AddMaintenanceItemCommand", "definition", "独立建案初始化完成后不能继续添加保全项");
        }
        if (source != null && (evidence == null || !evidence.authoritative())) {
            throw new MaintenanceValidationException("AddMaintenanceItemCommand", "selectionEvidence",
                    "独立建案必须提供权威项目选择证据");
        }
        if (!plannedItemCodes.isEmpty() && !plannedItemCodes.contains(item.itemCode())) {
            throw new MaintenanceValidationException("AddMaintenanceItemCommand", "definition",
                    "保全项不在本次建案计划中: " + item.itemCode());
        }
        if (itemInstances.stream().anyMatch(existing -> !existing.isCompatibleWith(item))) {
            throw new MaintenanceValidationException("AddMaintenanceItemCommand", "definition",
                    "保全项与案件内已有项目互斥: " + item.itemCode());
        }
        AggregateLifecycle
                .apply(new MaintenanceItemAddedEvent(command.id(), item, addedAt, command.createdBy(), tenantId));
    }

    /** 全部计划项目已冻结后才允许独立案件进入信息录入。 */
    @CommandHandler
    public void handle(CompleteMaintenanceCaseInitializationCommand command) {
        requireSameTenant(command.tenantId());
        requireItemStatusMutable("COMPLETE_CASE_INITIALIZATION");
        if (!plannedItemCodes.equals(command.itemCodes())) {
            throw new MaintenanceValidationException("CompleteMaintenanceCaseInitializationCommand", "itemCodes",
                    "初始化项目与建案计划不一致");
        }
        List<String> frozenItemCodes = itemInstances.stream().map(MaintenanceItemInstance::itemCode).toList();
        if (!plannedItemCodes.equals(frozenItemCodes)
                || itemInstances.stream().anyMatch(item -> !item.selectionEvidence().authoritative())) {
            throw new MaintenanceValidationException("CompleteMaintenanceCaseInitializationCommand", "itemCodes",
                    "仍有保全项未冻结权威配置证据");
        }
        if (!initializationCompleted) {
            AggregateLifecycle.apply(new MaintenanceCaseInitializationCompletedEvent(command.id(), command.itemCodes(),
                    LocalDateTime.now(), command.completedBy(), tenantId));
        }
        workflow.initializeWorkflow(id, itemInstances, command.completedBy());
    }

    // 流程任务清单初始化事件：业务状态由 @AggregateMember 成员实体（MaintenanceWorkflow）的同名
    // 事件处理器重建，聚合根侧仅维护审计字段——双监听为设计意图，勿合并或迁移。
    @EventSourcingHandler
    public void on(MaintenanceWorkflowInitializedEvent event) {
        this.updateTime = event.initializedAt();
        this.updatedBy = event.initializedBy();
    }

    // 流程任务命令处理器（Claim / Start / Fail / Retry / DecideCondition / DecideReview /
    // DecideUnderwriting / RecordPremiumQuote / RecordPremiumSettlement / FailPremiumSettlement /
    // CompleteItem）已随成员实体 MaintenanceWorkflow 下沉（DSN-01c 批 3），Axon 按 @AggregateMember
    // 自动路由——勿在聚合根重建同名处理器。依赖保全项状态或字段事实的处理器（CompleteWorkflowTask /
    // RecordDocument / CompleteCaseInitialization）留驻聚合根，经 workflow 成员转发。

    /** 完成信息录入或业务校验任务。 */
    @CommandHandler
    public void handle(CompleteMaintenanceWorkflowTaskCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceWorkflowTask task = workflow.findWorkflowTask(command.taskId());
        if (task.stepType() == MaintenanceStepType.DATA_ENTRY
                && findItem(task.itemCode()).fieldChanges().isEmpty()) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceWorkflowTaskCommand", "fieldChanges", "信息录入至少需要一项实际字段变更");
        }
        MaintenanceWorkflowOperation operation = workflow.workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.COMPLETE, command.taskId(), command.evidenceVersion(), command.evidenceHash(),
                command.resultCode(), command.reason(), command.operatorId());
        workflow.transition(command.taskId(), operation, currentTask -> currentTask.complete(operation), true);
    }

    /** 为案件建立未来生效计划；此时不冻结 Policy 请求证据。 */
    @CommandHandler
    public void handle(ScheduleMaintenanceEffectCommand command) {
        requireSameTenant(command.tenantId());
        if (!initializationCompleted || workflow.getWorkflowTasks().isEmpty()
                || workflow.activeEffectTaskIds().isEmpty()) {
            throw new MaintenanceValidationException(
                    "ScheduleMaintenanceEffectCommand", "workflow", "案件流程尚未完成初始化或缺少生效任务");
        }
        LocalDateTime scheduledAt = LocalDateTime.now();
        MaintenanceEffectSchedule candidate = MaintenanceEffectSchedule.create(
                command.scheduleId(), effectiveTimeType, command.tenantZoneId(),
                command.nextExecutionAt(), scheduledAt);
        MaintenanceEffectSchedule existingSchedule = effectPlan.getEffectSchedule();
        if (existingSchedule != null) {
            if (existingSchedule.samePlan(
                    candidate.scheduleId(), candidate.tenantZoneId(), candidate.nextExecutionAt())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "ScheduleMaintenanceEffectCommand", "schedule", "案件已存在不同的未来生效计划");
        }
        AggregateLifecycle.apply(new MaintenanceEffectScheduledEvent(id, candidate, command.operatorId(), tenantId));
        changeEffectStatus(workflow.activeEffectTaskIds().getFirst(), MaintenanceEffectStatus.SCHEDULED,
                "未来生效计划已建立", command.operatorId());
    }

    // 计划族三个处理器（PauseMaintenanceEffectScheduleCommand / RecordMaintenanceEffectScheduleAttemptCommand /
    // CompleteMaintenanceEffectScheduleCommand）已随成员实体 MaintenanceEffectPlan 下沉（DSN-01c 批 4），Axon 按
    // @AggregateMember 自动路由——勿在聚合根重建同名处理器。建立 / 恢复 / 记录失败三个处理器因依赖流程任务成员
    // （活跃生效任务查询与失败任务重试）留驻聚合根，经 effectPlan 的包级方法转发。

    /** 恢复暂停或失败计划，并将可重试的生效任务恢复到 READY。 */
    @CommandHandler
    public void handle(ResumeMaintenanceEffectScheduleCommand command) {
        requireSameTenant(command.tenantId());
        effectPlan.requireSchedule(command.scheduleId());
        if (command.nextExecutionAt() == null || command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "ResumeMaintenanceEffectScheduleCommand", "resume", "恢复时间和原因不能为空");
        }
        if (effectPlan.getEffectSchedule().status() == MaintenanceEffectScheduleStatus.ACTIVE) {
            return;
        }
        LocalDateTime resumedAt = LocalDateTime.now();
        effectPlan.resume(command.nextExecutionAt(), resumedAt);
        workflow.retryFailedEffectTasks(command.operationId(), command.reason(), command.operatorId());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleResumedEvent(
                id, effectPlan.getEffectSchedule().scheduleId(), command.nextExecutionAt(), command.reason().trim(),
                resumedAt, command.operatorId(), tenantId));
        changeEffectStatus(workflow.activeEffectTaskIds().getFirst(), MaintenanceEffectStatus.SCHEDULED,
                "未来生效计划已恢复", command.operatorId());
    }

    /** 记录计划失败；可重试失败同时恢复生效任务并继续保持计划状态。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectScheduleFailureCommand command) {
        requireSameTenant(command.tenantId());
        effectPlan.requireSchedule(command.scheduleId());
        if (effectPlan.currentEffectStatus() == MaintenanceEffectStatus.APPLIED) {
            return;
        }
        LocalDateTime failedAt = LocalDateTime.now();
        effectPlan.recordFailure(
                command.attemptId(), command.errorCode(), command.errorMessage(),
                command.retryAt(), command.terminal(), failedAt);
        if (!command.terminal()) {
            workflow.retryFailedEffectTasks(command.attemptId() + ":retry", command.errorMessage(), command.operatorId());
        }
        AggregateLifecycle.apply(new MaintenanceEffectScheduleFailedEvent(
                id, effectPlan.getEffectSchedule().scheduleId(), command.attemptId(), command.errorCode(),
                command.errorMessage(), command.retryAt(), command.terminal(), failedAt,
                command.operatorId(), tenantId));
        changeEffectStatus(workflow.activeEffectTaskIds().getFirst(),
                command.terminal() ? MaintenanceEffectStatus.FAILED : MaintenanceEffectStatus.SCHEDULED,
                command.errorMessage(), command.operatorId());
    }

    /** 在一个聚合命令事务中冻结案件全部生效任务。 */
    @CommandHandler
    public void handle(RequestMaintenanceCaseEffectCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenanceEffectRequestEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RequestMaintenanceCaseEffectCommand", "evidence", "生效请求证据不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId), MaintenanceWorkflowAction.REQUEST_EFFECT,
                    taskId, evidence.evidenceVersion(), evidence.requestPayloadHash(),
                    MaintenanceEffectStatus.EFFECTING.getCode(), null, command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.requestEffect(evidence, operation), false);
        }
        if (applied) {
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.EFFECTING, "案件级 Policy 应用请求已冻结",
                    command.operatorId());
        }
    }

    /** 在一个聚合命令事务中为案件全部生效任务记录同一权威回执。 */
    @CommandHandler
    public void handle(RecordMaintenanceCasePolicyApplicationCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenancePolicyApplicationEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCasePolicyApplicationCommand", "evidence",
                    "Policy 应用回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION, taskId, evidence.evidenceVersion(),
                    evidence.applicationHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.endorsementNo(),
                    command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.recordPolicyApplication(evidence, operation), true);
        }
        if (applied) {
            if (!workflow.allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCasePolicyApplicationCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级 Policy 权威回执已记录",
                    command.operatorId());
            if (effectPlan.isEffectCompensationRequired() && effectPlan.getEffectCompensationEvidence() != null) {
                AggregateLifecycle.apply(
                        new MaintenanceEffectCompensationResolvedEvent(id, effectPlan.getEffectCompensationEvidence().compensationId(),
                                evidence.endorsementNo(), LocalDateTime.now(), command.operatorId(), tenantId));
            }
        }
    }

    /**
     * 在一个聚合命令事务中为案件全部生效任务记录同一投资账户转换回执。
     * <p>
     * 与 {@link #handle(RecordMaintenanceCasePolicyApplicationCommand)} 并列的第二条生效收口路径：走投资账户
     * 转换出口的案件由此收口，两条路径的操作动作与回执证据类型均不同，不可交叉。
     * </p>
     */
    @CommandHandler
    public void handle(RecordMaintenanceCaseInvestmentSwitchCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenanceInvestmentSwitchEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCaseInvestmentSwitchCommand", "evidence",
                    "投资账户转换回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_INVESTMENT_SWITCH, taskId, evidence.evidenceVersion(),
                    evidence.contentHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.accountId(),
                    command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.recordInvestmentSwitch(evidence, operation), true);
        }
        if (applied) {
            if (!workflow.allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCaseInvestmentSwitchCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级投资账户转换回执已记录",
                    command.operatorId());
        }
    }

    /**
     * 在一个聚合命令事务中为案件全部生效任务记录同一保单贷款放款回执（g04-03）。
     * <p>
     * 与 {@link #handle(RecordMaintenanceCasePolicyApplicationCommand)}、{@link #handle(RecordMaintenanceCaseInvestmentSwitchCommand)}
     * 并列的第三条生效收口路径：走保单贷款出口的案件由此收口，三条路径的操作动作与回执证据类型均不同，不可交叉。
     * </p>
     */
    @CommandHandler
    public void handle(RecordMaintenanceCaseLoanDisbursementCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenanceLoanDisbursementEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCaseLoanDisbursementCommand", "evidence",
                    "保单贷款放款回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_LOAN_DISBURSEMENT, taskId, evidence.evidenceVersion(),
                    evidence.contentHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.loanId(),
                    command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.recordLoanDisbursement(evidence, operation), true);
        }
        if (applied) {
            if (!workflow.allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCaseLoanDisbursementCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级保单贷款放款回执已记录",
                    command.operatorId());
        }
    }

    /**
     * 在一个聚合命令事务中为案件全部生效任务记录同一保单贷款还款回执（g04-04）。
     * <p>
     * 与 {@link #handle(RecordMaintenanceCasePolicyApplicationCommand)}、{@link #handle(RecordMaintenanceCaseInvestmentSwitchCommand)}、
     * {@link #handle(RecordMaintenanceCaseLoanDisbursementCommand)} 并列的第四条生效收口路径：走保单贷款还款出口的
     * 案件由此收口，四条路径的操作动作与回执证据类型均不同，不可交叉。
     * </p>
     */
    @CommandHandler
    public void handle(RecordMaintenanceCaseLoanRepaymentCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenanceLoanRepaymentEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCaseLoanRepaymentCommand", "evidence",
                    "保单贷款还款回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_LOAN_REPAYMENT, taskId, evidence.evidenceVersion(),
                    evidence.contentHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.repaymentId(),
                    command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.recordLoanRepayment(evidence, operation), true);
        }
        if (applied) {
            if (!workflow.allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCaseLoanRepaymentCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级保单贷款还款回执已记录",
                    command.operatorId());
        }
    }

    /**
     * 在一个聚合命令事务中为案件全部生效任务记录同一保单补发单证请求回执（g04-09）。
     * <p>
     * 与 {@link #handle(RecordMaintenanceCasePolicyApplicationCommand)}、{@link #handle(RecordMaintenanceCaseInvestmentSwitchCommand)}、
     * {@link #handle(RecordMaintenanceCaseLoanDisbursementCommand)}、{@link #handle(RecordMaintenanceCaseLoanRepaymentCommand)}
     * 并列的第五条生效收口路径：走保单补发出口的案件由此收口，五条路径的操作动作与回执证据类型均不同，不可交叉。
     * </p>
     * <p>
     * 🔴 本处理器只记录<b>请求事实已交付对端</b>这一回执，不校验对端是否已渲染补发件——渲染寄送归 G13，不在
     * 本域生效判据内（与批单出口同边界）。
     * </p>
     */
    @CommandHandler
    public void handle(RecordMaintenanceCasePolicyReissueCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        MaintenancePolicyReissueEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCasePolicyReissueCommand", "evidence",
                    "保单补发单证请求回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_POLICY_REISSUE, taskId, evidence.evidenceVersion(),
                    evidence.contentHash(), MaintenanceEffectStatus.APPLIED.getCode(),
                    evidence.reissueRequestId(), command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.recordPolicyReissue(evidence, operation), true);
        }
        if (applied) {
            if (!workflow.allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCasePolicyReissueCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级保单补发单证请求回执已记录",
                    command.operatorId());
        }
    }

    /** 出具保全凭证并完成凭证步骤；模板编码以案件冻结配置为准，不接受调用方自报。 */
    @CommandHandler
    public void handle(RecordMaintenanceDocumentCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceDocumentEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceDocumentCommand", "evidence", "凭证出具事实不能为空");
        }
        requireDocumentTemplate(workflow.findWorkflowTask(command.taskId()), evidence);
        MaintenanceWorkflowOperation operation = workflow.workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.RECORD_DOCUMENT, command.taskId(), evidence.templateCode(),
                evidence.contentHash(), MaintenanceWorkflowTaskStatus.COMPLETED.getCode(),
                evidence.voucherNo(), command.operatorId());
        workflow.transition(command.taskId(), operation, task -> task.recordDocument(evidence, operation), true);
    }

    /** 在一个聚合命令事务中将案件全部已发起生效任务置为失败。 */
    @CommandHandler
    public void handle(FailMaintenanceCaseEffectCommand command) {
        requireSameTenant(command.tenantId());
        List<String> taskIds = workflow.requireAllEffectTaskIds(command.taskIds());
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflow.workflowOperation(
                    workflow.caseTaskOperationId(command.operationId(), taskId), MaintenanceWorkflowAction.FAIL_EFFECT,
                    taskId, null, null, command.failureCode(), command.failureReason(), command.operatorId());
            applied |= workflow.transition(taskId, operation, task -> task.failEffect(operation), false);
        }
        if (applied) {
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.FAILED, command.failureReason(),
                    command.operatorId());
        }
    }

    /** Policy 已成功而案件回执写入失败时，记录独立人工补偿事实。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectCompensationCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceEffectCompensationEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "evidence",
                    "补偿事实不能为空");
        }
        workflow.requireEffectCompensationContext(command.taskId(), evidence);
        if (effectPlan.getEffectCompensationEvidence() != null) {
            if (effectPlan.getEffectCompensationEvidence().equals(evidence)) {
                return;
            }
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "evidence",
                    "案件已存在不同的补偿事实");
        }
        AggregateLifecycle.apply(new MaintenanceEffectCompensationRequiredEvent(id, command.taskId(), evidence,
                evidence.recordedAt(), command.operatorId(), tenantId));
        changeEffectStatus(command.taskId(), MaintenanceEffectStatus.FAILED, evidence.failureReason(),
                command.operatorId());
    }

    // 流程任务迁移事件：业务状态由 @AggregateMember 成员实体（MaintenanceWorkflow）的同名事件
    // 处理器重建，聚合根侧仅维护审计字段——双监听为设计意图，勿合并或迁移。
    @EventSourcingHandler
    public void on(MaintenanceWorkflowTaskTransitionedEvent event) {
        this.updateTime = event.transitionedAt();
        this.updatedBy = event.operatedBy();
    }

    // 生效族事件：业务状态由 @AggregateMember 成员实体（MaintenanceEffectPlan）的同名事件处理器重建，
    // 聚合根侧仅维护审计字段与跨成员联动（生效状态变更还须联动案件状态与结算 / 流程成员镜像）
    // ——双监听为设计意图，勿合并或迁移。
    @EventSourcingHandler
    public void on(MaintenanceEffectStatusChangedEvent event) {
        if (event.currentStatus() == MaintenanceEffectStatus.APPLIED) {
            this.status = MaintenanceStatus.COMPLETED;
            if (settlement != null) {
                settlement.markStatus(MaintenanceStatus.COMPLETED);
            }
            if (workflow != null) {
                workflow.markStatus(MaintenanceStatus.COMPLETED);
            }
        }
        this.updateTime = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    /**
     * 批单出具回放。
     * <p>
     * 本事件是「案件已生效」这一事实的**派生凭证**，不改变聚合任何业务状态——回放时只推进审计时间。
     * 其存在意义在于把批单要素固化进事件流（见 {@link MaintenanceEndorsementIssuedEvent} javadoc）。
     * </p>
     */
    @EventSourcingHandler
    public void on(MaintenanceEndorsementIssuedEvent event) {
        this.updateTime = event.issuedAt();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduledEvent event) {
        this.updateTime = event.schedule().createdAt();
        this.updatedBy = event.scheduledBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectSchedulePausedEvent event) {
        this.updateTime = event.pausedAt();
        this.updatedBy = event.pausedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleResumedEvent event) {
        this.updateTime = event.resumedAt();
        this.updatedBy = event.resumedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleAttemptedEvent event) {
        this.updateTime = event.attemptedAt();
        this.updatedBy = event.attemptedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleFailedEvent event) {
        this.updateTime = event.failedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleCompletedEvent event) {
        this.updateTime = event.completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationRequiredEvent event) {
        this.updateTime = event.recordedAt();
        this.updatedBy = event.recordedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationResolvedEvent event) {
        this.updateTime = event.resolvedAt();
        this.updatedBy = event.resolvedBy();
    }

    // 追溯三段链路事件：业务状态由 @AggregateMember 成员实体（MaintenanceRetroactiveResolution）
    // 的同名事件处理器重建，聚合根侧仅维护审计字段——双监听为设计意图，勿合并或迁移。
    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisStartedEvent event) {
        this.updateTime = event.analysis().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisCompletedEvent event) {
        this.updateTime = event.analysis().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisFailedEvent event) {
        this.updateTime = event.analysis().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationStartedEvent event) {
        this.updateTime = event.recalculation().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveProductRecalculationRecordedEvent event) {
        this.updateTime = event.recalculation().updatedAt();
        this.updatedBy = event.recordedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationCompletedEvent event) {
        this.updateTime = event.recalculation().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationFailedEvent event) {
        this.updateTime = event.recalculation().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionStartedEvent event) {
        this.updateTime = event.resolution().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionCompletedEvent event) {
        this.updateTime = event.resolution().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionFailedEvent event) {
        this.updateTime = event.resolution().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseRejectedByReviewEvent event) {
        this.status = MaintenanceStatus.REJECTED;
        if (settlement != null) {
            settlement.markStatus(MaintenanceStatus.REJECTED);
        }
        if (workflow != null) {
            workflow.markStatus(MaintenanceStatus.REJECTED);
        }
        this.updateTime = event.rejectedAt();
        this.updatedBy = event.rejectedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseRejectedByUnderwritingEvent event) {
        this.status = MaintenanceStatus.REJECTED;
        if (settlement != null) {
            settlement.markStatus(MaintenanceStatus.REJECTED);
        }
        if (workflow != null) {
            workflow.markStatus(MaintenanceStatus.REJECTED);
        }
        this.updateTime = event.rejectedAt();
        this.updatedBy = event.rejectedBy();
    }

    /** 冻结项目撤销请求；已发起或已完成 Policy 生效的项目必须改走反向保全。 */
    @CommandHandler
    public MaintenanceItemWithdrawal handle(StartMaintenanceItemWithdrawalCommand command) {
        requireSameTenant(command.tenantId());
        requireItemStatusMutable("WITHDRAW_MAINTENANCE_ITEM");
        if (!initializationCompleted) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "initializationCompleted", "案件未初始化完成");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "reason", "撤销原因不能为空");
        }
        MaintenanceItemWithdrawal existing = currentWithdrawal(command.itemCode());
        if (existing != null) {
            if (existing.sameRequest(command.operationId(), command.requestHash())) {
                return existing;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "operationId", "保全项目已被其他撤销请求占用");
        }
        findItem(command.itemCode());
        if (activeItemCount() <= 1) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "itemCode", "首批项目撤销只允许多项目案件使用");
        }
        if (effectPlan.currentEffectStatus() == MaintenanceEffectStatus.EFFECTING
                || effectPlan.currentEffectStatus() == MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "effectStatus", "Policy 生效已发起，必须新建反向保全案件");
        }
        if (workflow.itemTasks(command.itemCode()).stream()
                .filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                .anyMatch(task -> task.effectEvidence() != null && task.effectEvidence().isApplied())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "itemCode", "项目已经完成 Policy 生效，必须新建反向保全案件");
        }
        MaintenanceWorkflowTask feeTask = sourceFeeTask(command.itemCode());
        LocalDateTime requestedAt = LocalDateTime.now();
        MaintenanceItemWithdrawal withdrawal = MaintenanceItemWithdrawal.requested(
                command.itemCode(), command.operationId(), command.requestHash(), command.reason(),
                feeTask == null ? null : feeTask.billingPostingEvidence(),
                feeTask == null ? null : feeTask.fundSettlementEvidence(),
                requestedAt, command.operatorId());
        AggregateLifecycle.apply(new MaintenanceItemWithdrawalStartedEvent(id, withdrawal, tenantId));
        return withdrawal;
    }

    /** 冻结自动恢复所需的支付渠道；空渠道的退款补偿不需要额外恢复上下文。 */
    @CommandHandler
    public void handle(ConfigureMaintenanceItemWithdrawalRecoveryCommand command) {
        requireSameTenant(command.tenantId());
        requireWithdrawal(command.itemCode(), command.operationId(), command.requestHash());
        MaintenanceItemWithdrawalRecoveryContext context = new MaintenanceItemWithdrawalRecoveryContext(
                command.itemCode(), command.operationId(), command.requestHash(), command.paymentMethod(),
                LocalDateTime.now(), command.configuredBy());
        MaintenanceItemWithdrawalRecoveryContext existing = currentWithdrawalRecoveryContext(command.itemCode());
        if (existing != null) {
            if (existing.operationId().equals(context.operationId())
                    && existing.requestHash().equalsIgnoreCase(context.requestHash())
                    && Objects.equals(existing.paymentMethod(), context.paymentMethod())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "ConfigureMaintenanceItemWithdrawalRecoveryCommand", "paymentMethod", "撤销恢复上下文已经冻结，不能替换");
        }
        AggregateLifecycle.apply(new MaintenanceItemWithdrawalRecoveryConfiguredEvent(id, context, tenantId));
    }

    /** 财务补偿完成后撤销当前项目提案，并将该项目未完成任务显式置为已跳过。 */
    @CommandHandler
    public MaintenanceItemWithdrawal handle(RecordMaintenanceItemWithdrawalCompensationCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceItemWithdrawal withdrawal = requireWithdrawal(
                command.itemCode(), command.operationId(), command.requestHash());
        MaintenanceItemWithdrawal after = withdrawal.recordCompensation(command.compensation());
        if (after.equals(withdrawal)) {
            return after;
        }
        MaintenanceFieldConflictPlan proposedPlan = after.status() == MaintenanceItemWithdrawalStatus.COMPLETED
                ? withdrawalPlan(command.itemCode(), command.compensation().recordedAt())
                : null;
        AggregateLifecycle.apply(new MaintenanceItemWithdrawalCompensationRecordedEvent(
                id, after, proposedPlan, command.operatorId(), tenantId));
        if (after.status() == MaintenanceItemWithdrawalStatus.COMPLETED) {
            skipWithdrawnItemTasks(after, command.operatorId());
        }
        return after;
    }

    /** Billing 或 Payment 不可用时记录失败，不把外部失败伪装为项目已撤销。 */
    @CommandHandler
    public MaintenanceItemWithdrawal handle(FailMaintenanceItemWithdrawalCommand command) {
        requireSameTenant(command.tenantId());
        MaintenanceItemWithdrawal withdrawal = requireWithdrawal(
                command.itemCode(), command.operationId(), command.requestHash());
        MaintenanceItemWithdrawal failed = withdrawal.fail(
                command.failureCode(), command.failureMessage(), LocalDateTime.now());
        AggregateLifecycle.apply(new MaintenanceItemWithdrawalFailedEvent(
                id, failed, command.operatorId(), tenantId));
        return failed;
    }

    @EventSourcingHandler
    public void on(MaintenanceItemWithdrawalStartedEvent event) {
        if (itemWithdrawals == null) {
            itemWithdrawals = new HashMap<>();
        }
        itemWithdrawals.put(event.withdrawal().itemCode(), event.withdrawal());
        updateWithdrawalAudit(event.withdrawal(), event.withdrawal().requestedBy());
    }

    @EventSourcingHandler
    public void on(MaintenanceItemWithdrawalRecoveryConfiguredEvent event) {
        if (itemWithdrawalRecoveryContexts == null) {
            itemWithdrawalRecoveryContexts = new HashMap<>();
        }
        itemWithdrawalRecoveryContexts.put(event.recoveryContext().itemCode(), event.recoveryContext());
        this.updateTime = event.recoveryContext().configuredAt();
        this.updatedBy = event.recoveryContext().configuredBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceItemWithdrawalCompensationRecordedEvent event) {
        if (itemWithdrawals == null) {
            itemWithdrawals = new HashMap<>();
        }
        itemWithdrawals.put(event.withdrawal().itemCode(), event.withdrawal());
        if (event.withdrawal().status() == MaintenanceItemWithdrawalStatus.COMPLETED) {
            clearWithdrawnItemChanges(event.withdrawal().itemCode());
            if (event.proposedPlan() != null) {
                applyConflictPlan(event.proposedPlan());
            }
        }
        updateWithdrawalAudit(event.withdrawal(), event.operatedBy());
    }

    @EventSourcingHandler
    public void on(MaintenanceItemWithdrawalFailedEvent event) {
        if (itemWithdrawals == null) {
            itemWithdrawals = new HashMap<>();
        }
        itemWithdrawals.put(event.withdrawal().itemCode(), event.withdrawal());
        updateWithdrawalAudit(event.withdrawal(), event.operatedBy());
    }

    /** 使用当前 Policy 与目录权威证据生成并保存完整字段草稿。 */
    @CommandHandler
    public void handle(ProposeMaintenanceFieldChangesCommand command) {
        requireSameTenant(command.tenantId());
        requireItemEditingAllowed("PROPOSE_MAINTENANCE_FIELD_CHANGES");
        if (policySnapshot == null) {
            throw new MaintenanceValidationException("ProposeMaintenanceFieldChangesCommand", "currentPolicySnapshot",
                    "案件缺少 Policy 基准快照");
        }
        LocalDateTime recordedAt = LocalDateTime.now();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC);
        MaintenanceFieldProposalPlan plan = new MaintenanceFieldProposalPlanner().plan(id, tenantId, policySnapshot,
                command.currentPolicySnapshot(), itemInstances, command.itemCode(), command.proposals(),
                command.fieldCatalogSnapshot(), capturedAt);
        MaintenanceItemInstance item = findItem(command.itemCode());
        MaintenanceFieldCatalogSnapshot existingCatalog = fieldCatalogSnapshots.get(command.itemCode());
        if (item.fieldChanges().equals(plan.changes()) && proposedFieldValues.equals(plan.proposedFieldValues())
                && command.fieldCatalogSnapshot().sameAuthorityAs(existingCatalog)) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceFieldChangesRecordedEvent(command.id(), command.itemCode(),
                plan.changes(), recordedAt, command.updatedBy(), tenantId));
        AggregateLifecycle.apply(new MaintenanceProposedSnapshotRecordedEvent(command.id(), command.itemCode(),
                plan.proposedSnapshot(), plan.proposedFieldValues(), command.fieldCatalogSnapshot(), capturedAt,
                command.updatedBy(), tenantId));
    }

    /** 使用 Policy 最新结构化快照刷新案件字段冲突。 */
    @CommandHandler
    public MaintenanceFieldConflictPlan handle(RefreshMaintenanceFieldConflictsCommand command) {
        requireSameTenant(command.tenantId());
        requireConflictMutable("REFRESH_MAINTENANCE_FIELD_CONFLICTS");
        validateConflictContext(command.currentPolicySnapshot());
        if (conflictOperationAlreadyApplied(command.operationId(), command.requestHash())) {
            return currentConflictPlan();
        }
        MaintenanceFieldConflictPlan plan = new MaintenanceFieldConflictPlanner().refresh(
                id, tenantId, command.currentPolicySnapshot(), itemInstances, command.refreshedAt());
        AggregateLifecycle.apply(new MaintenanceFieldConflictsRefreshedEvent(
                id, command.operationId(), command.requestHash(), plan, command.refreshedAt(),
                command.refreshedBy(), tenantId));
        updateEffectStatusAfterConflictPlan(plan, command.refreshedBy());
        return plan;
    }

    /** 显式解决一个字段冲突并重建案件拟快照。 */
    @CommandHandler
    public MaintenanceFieldConflictPlan handle(ResolveMaintenanceFieldConflictCommand command) {
        requireSameTenant(command.tenantId());
        requireConflictMutable("RESOLVE_MAINTENANCE_FIELD_CONFLICT");
        if (command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "ResolveMaintenanceFieldConflictCommand", "reason", "冲突解决原因不能为空");
        }
        if (conflictOperationAlreadyApplied(command.operationId(), command.requestHash())) {
            return currentConflictPlan();
        }
        MaintenanceFieldChange before = findFieldChange(command.itemCode(), command.objectId(), command.fieldCode());
        MaintenanceFieldConflictPlan plan = new MaintenanceFieldConflictPlanner().resolve(
                id, tenantId, currentProposedSnapshot().policyVersion(), policyId.id(), itemInstances,
                proposedFieldValues, command.itemCode(), command.objectId(), command.fieldCode(), command.action(),
                command.reenteredValue(), command.resolvedAt());
        MaintenanceFieldChange after = plan.changesByItem().get(command.itemCode()).stream()
                .filter(change -> change.objectId().equals(command.objectId())
                        && change.fieldCode().equals(command.fieldCode()))
                .findFirst()
                .orElseThrow(() -> new MaintenanceValidationException(
                        "ResolveMaintenanceFieldConflictCommand", "fieldCode", "冲突解决计划缺少目标字段"));
        AggregateLifecycle.apply(new MaintenanceFieldConflictResolvedEvent(
                id, command.operationId(), command.requestHash(), before, after, command.action(),
                command.reason().trim(), plan, command.resolvedAt(), command.resolvedBy(), tenantId));
        updateEffectStatusAfterConflictPlan(plan, command.resolvedBy());
        return plan;
    }

    // 结算族命令处理器（CalculateMaintenancePremium / PremiumAdjustment / SurrenderValue / PremiumPosting /
    // FinancialSettlement）已随成员实体 MaintenanceSettlement 下沉（DSN-01c），Axon 按 @AggregateMember
    // 自动路由——勿在聚合根重建同名处理器。

    // 处理执行保全命令
    @CommandHandler
    public void handle(ExecuteMaintenanceCommand command) {
        requireSameTenant(command.tenantId());
        if (requiresPremiumSettlement() && !settlement.hasRecordedBalanceFact()) {
            throw new MaintenanceStatusException(id.id(), settlement.getPremiumSettlementStatus().name(),
                    "EXECUTE_MAINTENANCE", "价格影响型保全必须先完成 Product 差额和 Billing 余额事实登记");
        }
        // enrich policyId 与 maintenanceType 作为跨域上下文，供 policy 域监听回写保单状态
        AggregateLifecycle.apply(new MaintenanceExecutedEvent(command.id(), this.policyId.id(), this.maintenanceType,
                command.effectiveTime(), command.executionDetails(), LocalDateTime.now(), command.updatedBy(),
                this.tenantId));
    }

    // 处理状态变更事件
    @EventSourcingHandler
    public void on(MaintenanceStatusChangedEvent event) {
        this.status = event.newStatus();
        if (settlement != null) {
            settlement.markStatus(event.newStatus());
        }
        if (workflow != null) {
            workflow.markStatus(event.newStatus());
        }
        this.updateTime = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    // 处理变更记录添加事件
    @EventSourcingHandler
    public void on(MaintenanceChangeAddedEvent event) {
        this.changes.add(new MaintenanceChange(event.changeType(), event.fieldName(), event.oldValue(),
                event.newValue(), event.createdAt()));
        this.updateTime = event.createdAt();
        this.updatedBy = event.createdBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceItemAddedEvent event) {
        this.itemInstances.add(event.item());
        this.updateTime = event.addedAt();
        this.updatedBy = event.createdBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceFieldChangesRecordedEvent event) {
        MaintenanceItemInstance item = findItem(event.itemCode());
        MaintenanceItemInstance updated = item.withFieldChanges(event.changes());
        int index = this.itemInstances.indexOf(item);
        this.itemInstances.set(index, updated);
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceFieldConflictsRefreshedEvent event) {
        applyConflictPlan(event.plan());
        recordConflictOperation(event.operationId(), event.operationHash());
        this.updateTime = event.refreshedAt().toLocalDateTime();
        this.updatedBy = event.refreshedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceFieldConflictResolvedEvent event) {
        applyConflictPlan(event.plan());
        recordConflictOperation(event.operationId(), event.operationHash());
        this.updateTime = event.resolvedAt().toLocalDateTime();
        this.updatedBy = event.resolvedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceProposedSnapshotRecordedEvent event) {
        if (snapshotSet == null) {
            snapshotSet = MaintenanceSnapshotSet.capturedBefore(policySnapshot.beforeSnapshot());
        }
        this.snapshotSet = snapshotSet.attachProposed(event.proposedSnapshot());
        this.proposedFieldValues = new TreeMap<>(event.proposedFieldValues());
        this.fieldCatalogSnapshots.put(event.itemCode(), event.fieldCatalogSnapshot());
    }

    // 结算族事件：业务状态由 @AggregateMember 成员实体（MaintenanceSettlement）
    // 的同名事件处理器重建，聚合根侧仅维护审计字段——双监听为设计意图，勿合并或迁移。
    @EventSourcingHandler
    public void on(MaintenancePremiumCalculatedEvent event) {
        this.updateTime = event.updatedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumAdjustmentRecordedEvent event) {
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceSurrenderValueRecordedEvent event) {
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumPostingRecordedEvent event) {
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceFinancialSettlementRecordedEvent event) {
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    // 处理执行保全事件
    @EventSourcingHandler
    public void on(MaintenanceExecutedEvent event) {
        this.status = MaintenanceStatus.COMPLETED;
        if (settlement != null) {
            settlement.markStatus(MaintenanceStatus.COMPLETED);
        }
        if (workflow != null) {
            workflow.markStatus(MaintenanceStatus.COMPLETED);
        }
        this.updateTime = event.updatedAt();
        this.updatedBy = event.updatedBy();
    }

    // Getters (for query purposes)
    public MaintenanceId getId() {
        return id;
    }

    public PolicyId getPolicyId() {
        return policyId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public MaintenanceType getMaintenanceType() {
        return maintenanceType;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public EffectiveTimeType getEffectiveTimeType() {
        return effectiveTimeType;
    }

    public LocalDateTime getSpecificEffectiveDate() {
        return specificEffectiveDate;
    }

    // 🔴 @JsonIgnore 不可删（D-501-19）：以下结算族转发 getter 无对应字段，Jackson 会把它们
    //    写进载荷（快照/事件），反序列化时无写入面 ⇒ UnrecognizedPropertyException → 聚合无法加载。
    @JsonIgnore
    public Money getTotalAmount() {
        return settlement == null ? null : settlement.getTotalAmount();
    }

    @JsonIgnore
    public Money getRefundAmount() {
        return settlement == null ? null : settlement.getRefundAmount();
    }

    public String getDescription() {
        return description;
    }

    public List<MaintenanceChange> getChanges() {
        return changes == null ? null : List.copyOf(changes);
    }

    public List<MaintenanceItemInstance> getItemInstances() {
        return List.copyOf(itemInstances);
    }

    public List<String> getPlannedItemCodes() {
        return List.copyOf(plannedItemCodes);
    }

    public boolean isInitializationCompleted() {
        return initializationCompleted;
    }

    public MaintenanceChannel getSource() {
        return source;
    }

    public String getClientRequestKey() {
        return clientRequestKey;
    }

    public String getCreationRequestFingerprint() {
        return creationRequestFingerprint;
    }

    public PolicyMaintenanceSnapshot getPolicySnapshot() {
        return policySnapshot;
    }

    public MaintenanceSnapshotSet getSnapshotSet() {
        return snapshotSet;
    }

    public Map<String, MaintenanceFieldValue> getProposedFieldValues() {
        return Map.copyOf(proposedFieldValues);
    }

    public Map<String, MaintenanceFieldCatalogSnapshot> getFieldCatalogSnapshots() {
        return Map.copyOf(fieldCatalogSnapshots);
    }

    /**
     * 冲突操作幂等账本（operationId → 请求指纹哈希）。
     * <p>🔴 此 getter 不可删（DSN-01c 批 4 评估修复）：Jackson 只把「有 getter 的字段」纳入序列化属性，
     * 无 getter 的字段不进快照；而 Axon 恢复聚合时被快照覆盖的事件不再重放，由事件回放重建的账本条目
     * 会随快照恢复整体丢失——同一 operationId 的重试将绕过幂等保护（重复追加冲突事实）。
     * 账本必须跨快照存活，故必须暴露本 getter。返回只读拷贝、null 防御覆盖「旧快照无此键」的恢复路径
     * （与 {@link #getItemWithdrawals()} 同法）。</p>
     */
    public Map<String, String> getFieldConflictOperationHashes() {
        return fieldConflictOperationHashes == null ? Map.of() : Map.copyOf(fieldConflictOperationHashes);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    private void assertSameCreationRequest(CreateMaintenanceCaseCommand command) {
        if (source != command.idempotencyKey().source() || !Objects.equals(tenantId, command.tenantId())
                || !Objects.equals(clientRequestKey, command.idempotencyKey().clientRequestKey())
                || !Objects.equals(creationRequestFingerprint, command.requestFingerprint())) {
            throw new MaintenanceConflictException("CreateMaintenanceCaseCommand", "clientRequestKey",
                    "同一幂等键不能用于不同的建案请求");
        }
    }

    private void capturePolicySnapshot(CreateMaintenanceCaseCommand command, LocalDateTime recordedAt) {
        if (policySnapshot == null) {
            AggregateLifecycle.apply(new MaintenancePolicySnapshotCapturedEvent(command.id(), command.policySnapshot(),
                    recordedAt, command.createdBy(), command.tenantId()));
            return;
        }
        if (!policySnapshot.sameBusinessBaseline(command.policySnapshot())) {
            throw new MaintenanceValidationException("CreateMaintenanceCaseCommand", "policySnapshot",
                    "同一案件不能绑定不同的Policy基准快照");
        }
    }

    private void planItems(CreateMaintenanceCaseCommand command, LocalDateTime plannedAt) {
        if (plannedItemCodes == null || plannedItemCodes.isEmpty()) {
            AggregateLifecycle.apply(new MaintenanceCaseItemsPlannedEvent(command.id(), command.selectedItemCodes(),
                    plannedAt, command.createdBy(), command.tenantId()));
            return;
        }
        if (!plannedItemCodes.equals(command.selectedItemCodes())) {
            throw new MaintenanceValidationException("CreateMaintenanceCaseCommand", "selectedItemCodes",
                    "同一案件不能变更已计划保全项");
        }
    }

    // 🔴 @JsonIgnore 不可删（D-501-19）：结算族转发 getter 无对应字段，说明见 getTotalAmount 处注释块。
    @JsonIgnore
    public MaintenancePremiumSettlementStatus getPremiumSettlementStatus() {
        return settlement == null ? null : settlement.getPremiumSettlementStatus();
    }

    // 🔴 以下四个转发 getter 不可删（聚合对外读取生效状态的 API 面），且 @JsonIgnore 不可删（D-501-19）：
    //    字段已随成员实体 MaintenanceEffectPlan 维护，聚合根侧无对应写入面，序列化输出这些键会让反序列化
    //    触发 UnrecognizedPropertyException——生效状态已随 effectPlan 成员对象整体序列化，勿重复暴露。
    @JsonIgnore
    public MaintenanceEffectStatus getEffectStatus() {
        return effectPlan == null ? MaintenanceEffectStatus.NOT_STARTED : effectPlan.currentEffectStatus();
    }

    @JsonIgnore
    public MaintenanceEffectSchedule getEffectSchedule() {
        return effectPlan == null ? null : effectPlan.getEffectSchedule();
    }

    @JsonIgnore
    public boolean isEffectCompensationRequired() {
        return effectPlan != null && effectPlan.isEffectCompensationRequired();
    }

    @JsonIgnore
    public MaintenanceEffectCompensationEvidence getEffectCompensationEvidence() {
        return effectPlan == null ? null : effectPlan.getEffectCompensationEvidence();
    }

    /**
     * 生效计划成员实体（DSN-01c 批 4）。
     * <p>🔴 此 getter 不可删：Jackson 只把「有 getter 的字段」纳入序列化属性，无 getter 的字段
     * 不进 JSON ⇒ 快照恢复后成员状态整体丢失（且无判据可察）。序列化与反序列化依
     * 「getter + 私有字段」成对工作（字段即写入面，无需 setter）。</p>
     */
    public MaintenanceEffectPlan getEffectPlan() {
        return effectPlan;
    }

    /**
     * 追溯处置成员实体（DSN-01c）。
     * <p>🔴 此 getter 不可删：Jackson 只把「有 getter 的字段」纳入序列化属性，无 getter 的字段
     * 不进 JSON ⇒ 快照恢复后成员状态整体丢失（且无判据可察）。序列化与反序列化依
     * 「getter + 私有字段」成对工作（字段即写入面，无需 setter）。</p>
     */
    public MaintenanceRetroactiveResolution getRetroactiveResolution() {
        return retroactiveResolution;
    }

    /**
     * 费用结算成员实体（DSN-01c 批 2）。
     * <p>🔴 此 getter 不可删：Jackson 只把「有 getter 的字段」纳入序列化属性，无 getter 的字段
     * 不进 JSON ⇒ 快照恢复后成员状态整体丢失（且无判据可察）。序列化与反序列化依
     * 「getter + 私有字段」成对工作（字段即写入面，无需 setter）。</p>
     */
    public MaintenanceSettlement getSettlement() {
        return settlement;
    }

    /**
     * 流程任务成员实体（DSN-01c 批 3）。
     * <p>🔴 此 getter 不可删：Jackson 只把「有 getter 的字段」纳入序列化属性，无 getter 的字段
     * 不进 JSON ⇒ 快照恢复后成员状态整体丢失（且无判据可察）。序列化与反序列化依
     * 「getter + 私有字段」成对工作（字段即写入面，无需 setter）。</p>
     */
    public MaintenanceWorkflow getWorkflow() {
        return workflow;
    }

    // 🔴 @JsonIgnore 不可删（D-501-19）：以下三个转发 getter 无对应字段，Jackson 会把它们
    //    写进载荷（快照/事件），反序列化时无写入面 ⇒ UnrecognizedPropertyException → 聚合无法加载。
    @JsonIgnore
    public MaintenanceRetroactiveImpactAnalysis getRetroactiveImpactAnalysis() {
        return retroactiveResolution == null ? null : retroactiveResolution.getRetroactiveImpactAnalysis();
    }

    @JsonIgnore
    public MaintenanceRetroactivePeriodRecalculation getRetroactivePeriodRecalculation() {
        return retroactiveResolution == null ? null : retroactiveResolution.getRetroactivePeriodRecalculation();
    }

    @JsonIgnore
    public MaintenanceRetroactivePeriodResolution getRetroactivePeriodResolution() {
        return retroactiveResolution == null ? null : retroactiveResolution.getRetroactivePeriodResolution();
    }

    /** 流程任务清单（只读视图；转发成员实体）。 */
    // 🔴 @JsonIgnore 不可删（D-501-19）：转发 getter 无对应字段，序列化输出该键会让反序列化
    // 触发 UnrecognizedPropertyException——任务清单已随 workflow 成员对象整体序列化，勿重复暴露。
    @JsonIgnore
    public List<MaintenanceWorkflowTask> getWorkflowTasks() {
        return workflow == null ? List.of() : workflow.getWorkflowTasks();
    }

    /** 结算引用值对象（未经历结算记录事件时为空对象；分量判空即「未记录」）。 */
    // 🔴 @JsonIgnore 不可删（D-501-19）：结算族转发 getter 无对应字段，说明见 getTotalAmount 处注释块。
    @JsonIgnore
    public MaintenanceSettlementRefs getSettlementRefs() {
        return settlement == null ? MaintenanceSettlementRefs.empty() : settlement.getSettlementRefs();
    }

    // 🔴 @JsonIgnore 不可删（D-501-19）：以下结算族转发 getter 无对应字段，说明见 getTotalAmount 处注释块。
    @JsonIgnore
    public Integer getCommissionAdjustmentCount() {
        return settlement == null ? null : settlement.getCommissionAdjustmentCount();
    }

    @JsonIgnore
    public MaintenanceBalanceDirection getBalanceDirection() {
        return settlement == null ? null : settlement.getBalanceDirection();
    }

    @JsonIgnore
    public Money getBalance() {
        return settlement == null ? null : settlement.getBalance();
    }

    /** 退保政策快照值对象（未记录时为空对象；有效性以 {@code policyContentHash} 非空为准）。 */
    // 🔴 @JsonIgnore 不可删（D-501-19）：结算族转发 getter 无对应字段，说明见 getTotalAmount 处注释块。
    @JsonIgnore
    public SurrenderPolicySnapshot getSurrenderPolicy() {
        return settlement == null ? SurrenderPolicySnapshot.empty() : settlement.getSurrenderPolicy();
    }

    public Map<String, MaintenanceItemWithdrawal> getItemWithdrawals() {
        return itemWithdrawals == null ? Map.of() : Map.copyOf(itemWithdrawals);
    }

    public Map<String, MaintenanceItemWithdrawalRecoveryContext> getItemWithdrawalRecoveryContexts() {
        return itemWithdrawalRecoveryContexts == null ? Map.of() : Map.copyOf(itemWithdrawalRecoveryContexts);
    }


    private void requireItemEditingAllowed(String target) {
        requireItemStatusMutable(target);
        if (source != null && !initializationCompleted) {
            throw new MaintenanceStatusException(id.id(), status.name(), target, "独立建案尚未完成全部项目配置冻结");
        }
    }

    private void requireConflictMutable(String target) {
        requireItemStatusMutable(target);
        if (!initializationCompleted || itemInstances == null || itemInstances.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceFieldConflict", "itemInstances", "案件尚未完成保全项初始化");
        }
        if (effectPlan.currentEffectStatus() == MaintenanceEffectStatus.APPLIED
                || effectPlan.currentEffectStatus() == MaintenanceEffectStatus.EFFECTING) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflict", "effectStatus", "Policy 请求冻结后不能刷新或解决字段冲突");
        }
    }

    private void validateConflictContext(PolicyMaintenanceSnapshot snapshot) {
        if (snapshot == null || !policyId.equals(snapshot.policyId())) {
            throw new MaintenanceValidationException(
                    "RefreshMaintenanceFieldConflictsCommand", "currentPolicySnapshot", "Policy 当前快照与案件不匹配");
        }
        if (snapshot.policyVersion() < currentProposedSnapshot().policyVersion()) {
            throw new MaintenanceValidationException(
                    "RefreshMaintenanceFieldConflictsCommand", "currentPolicySnapshot", "Policy 当前版本不能早于案件期望版本");
        }
    }

    private MaintenanceFieldChange findFieldChange(String itemCode, String objectId, String fieldCode) {
        return findItem(itemCode).fieldChanges().stream()
                .filter(change -> Objects.equals(change.objectId(), objectId)
                        && Objects.equals(change.fieldCode(), fieldCode))
                .findFirst()
                .orElseThrow(() -> new MaintenanceValidationException(
                        "MaintenanceFieldConflict", "fieldCode", "案件中不存在指定字段变化"));
    }

    private MaintenanceFieldConflictPlan currentConflictPlan() {
        Map<String, List<MaintenanceFieldChange>> changesByItem = new TreeMap<>();
        itemInstances.forEach(item -> changesByItem.put(item.itemCode(), item.fieldChanges()));
        int conflictCount = Math.toIntExact(itemInstances.stream()
                .flatMap(item -> item.fieldChanges().stream())
                .filter(MaintenanceFieldChange::hasUnresolvedConflict)
                .count());
        return new MaintenanceFieldConflictPlan(
                changesByItem, proposedFieldValues, currentProposedSnapshot(), conflictCount);
    }

    private MaintenanceSnapshotReference currentProposedSnapshot() {
        if (snapshotSet == null || snapshotSet.proposedSnapshot() == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflict", "proposedSnapshot", "案件尚未形成拟变更快照");
        }
        return snapshotSet.proposedSnapshot();
    }

    private void applyConflictPlan(MaintenanceFieldConflictPlan plan) {
        if (plan.changesByItem().size() != itemInstances.size()
                || !plan.changesByItem().keySet().equals(
                        itemInstances.stream().map(MaintenanceItemInstance::itemCode).collect(Collectors.toSet()))) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflictPlan", "changesByItem", "冲突计划与案件保全项不一致");
        }
        List<MaintenanceItemInstance> updatedItems = new ArrayList<>(itemInstances.size());
        for (MaintenanceItemInstance item : itemInstances) {
            updatedItems.add(item.withFieldChanges(plan.changesByItem().get(item.itemCode())));
        }
        this.itemInstances = updatedItems;
        this.snapshotSet = snapshotSet.attachProposed(plan.proposedSnapshot());
        this.proposedFieldValues = new TreeMap<>(plan.proposedFieldValues());
    }

    private boolean conflictOperationAlreadyApplied(String operationId, String requestHash) {
        if (operationId == null || operationId.isBlank() || requestHash == null || requestHash.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflict", "operationId", "冲突操作号和请求摘要不能为空");
        }
        if (fieldConflictOperationHashes == null) {
            fieldConflictOperationHashes = new HashMap<>();
        }
        String existingHash = fieldConflictOperationHashes.get(operationId.trim());
        if (existingHash == null) {
            return false;
        }
        if (existingHash.equals(requestHash.trim())) {
            return true;
        }
        throw new MaintenanceValidationException(
                "MaintenanceFieldConflict", "operationId", "同一冲突操作号不能提交不同载荷");
    }

    private void recordConflictOperation(String operationId, String operationHash) {
        if (fieldConflictOperationHashes == null) {
            fieldConflictOperationHashes = new HashMap<>();
        }
        fieldConflictOperationHashes.put(operationId.trim(), operationHash.trim());
    }

    private void updateEffectStatusAfterConflictPlan(MaintenanceFieldConflictPlan plan, String changedBy) {
        MaintenanceEffectStatus current = effectPlan.currentEffectStatus();
        if (plan.conflictCount() > 0) {
            changeEffectStatus(conflictEffectTaskId(), MaintenanceEffectStatus.CONFLICTED,
                    "检测到顺序外字段冲突", changedBy);
            return;
        }
        if (current == MaintenanceEffectStatus.CONFLICTED) {
            MaintenanceEffectSchedule schedule = effectPlan.getEffectSchedule();
            MaintenanceEffectStatus restored = schedule != null
                    && schedule.status() != MaintenanceEffectScheduleStatus.COMPLETED
                            ? MaintenanceEffectStatus.SCHEDULED
                            : MaintenanceEffectStatus.NOT_STARTED;
            changeEffectStatus(conflictEffectTaskId(), restored, "字段冲突已全部解决", changedBy);
        }
    }

    private String conflictEffectTaskId() {
        return workflow.activeEffectTaskIds().stream().findFirst().orElseThrow(() -> new MaintenanceValidationException(
                "MaintenanceFieldConflict", "workflowTasks", "案件缺少生效流程任务"));
    }

    private void requireItemStatusMutable(String target) {
        if (status != MaintenanceStatus.PENDING && status != MaintenanceStatus.PROCESSING) {
            throw new MaintenanceStatusException(id.id(), status.name(), target, "只有待处理或处理中的保全案件可以修改保全项");
        }
    }

    private MaintenanceItemWithdrawal currentWithdrawal(String itemCode) {
        if (itemWithdrawals == null || itemCode == null) {
            return null;
        }
        return itemWithdrawals.get(itemCode.trim());
    }

    private MaintenanceItemWithdrawalRecoveryContext currentWithdrawalRecoveryContext(String itemCode) {
        if (itemWithdrawalRecoveryContexts == null || itemCode == null) {
            return null;
        }
        return itemWithdrawalRecoveryContexts.get(itemCode.trim());
    }

    private MaintenanceItemWithdrawal requireWithdrawal(
            String itemCode,
            String operationId,
            String requestHash) {
        MaintenanceItemWithdrawal withdrawal = currentWithdrawal(itemCode);
        if (withdrawal == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemWithdrawal", "itemCode", "项目撤销请求尚未冻结");
        }
        if (!withdrawal.sameRequest(operationId, requestHash)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemWithdrawal", "operationId", "项目撤销操作号或请求摘要不一致");
        }
        return withdrawal;
    }

    private long activeItemCount() {
        return itemInstances.stream()
                .filter(item -> {
                    MaintenanceItemWithdrawal withdrawal = currentWithdrawal(item.itemCode());
                    return withdrawal == null
                            || withdrawal.status() != MaintenanceItemWithdrawalStatus.COMPLETED;
                })
                .count();
    }

    private MaintenanceWorkflowTask sourceFeeTask(String itemCode) {
        List<MaintenanceWorkflowTask> postedFeeTasks = workflow.itemTasks(itemCode).stream()
                .filter(task -> task.stepType() == MaintenanceStepType.FEE_SETTLEMENT)
                .filter(task -> task.billingPostingEvidence() != null)
                .toList();
        if (postedFeeTasks.size() > 1) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemWithdrawal", "billingPosting", "同一项目存在多条 Billing 入账事实");
        }
        return postedFeeTasks.isEmpty() ? null : postedFeeTasks.getFirst();
    }

    private MaintenanceFieldConflictPlan withdrawalPlan(String itemCode, LocalDateTime withdrawnAt) {
        if (snapshotSet == null || snapshotSet.proposedSnapshot() == null) {
            return null;
        }
        return new MaintenanceFieldConflictPlanner().withdraw(
                id, tenantId, snapshotSet.proposedSnapshot().policyVersion(), policyId.id(),
                proposedFieldValues, itemInstances, itemCode,
                OffsetDateTime.of(withdrawnAt, ZoneOffset.UTC));
    }

    private void skipWithdrawnItemTasks(
            MaintenanceItemWithdrawal withdrawal,
            String operatorId) {
        LocalDateTime operatedAt = withdrawal.completedAt();
        workflow.itemTasks(withdrawal.itemCode()).stream()
                .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.COMPLETED)
                .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .forEach(task -> {
                    String operationId = withdrawal.operationId() + ":task:" + task.taskId();
                    MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                            operationId, MaintenanceWorkflowAction.WITHDRAW_ITEM, task.taskId(),
                            null, null, MaintenanceWorkflowTaskStatus.SKIPPED.getCode(),
                            withdrawal.reason(), operatedAt, operatorId);
                    MaintenanceWorkflowTask after = task.withdraw(operation);
                    AggregateLifecycle.apply(new MaintenanceWorkflowTaskTransitionedEvent(
                            id, task, after, null, null, operation.operationId(), operation.payloadHash(),
                            operatedAt, operatorId, tenantId));
                });
    }

    private void clearWithdrawnItemChanges(String itemCode) {
        MaintenanceItemInstance item = findItem(itemCode);
        int index = itemInstances.indexOf(item);
        itemInstances.set(index, item.withFieldChanges(List.of()));
    }

    private void updateWithdrawalAudit(
            MaintenanceItemWithdrawal withdrawal,
            String operatorId) {
        this.updateTime = withdrawal.updatedAt();
        this.updatedBy = operatorId;
    }

    private MaintenanceItemInstance findItem(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            throw new MaintenanceValidationException("Maintenance", "itemCode", "保全项编码不能为空");
        }
        return itemInstances.stream().filter(item -> item.itemCode().equals(itemCode)).findFirst().orElseThrow(
                () -> new MaintenanceValidationException("Maintenance", "itemCode", "案件中不存在保全项: " + itemCode));
    }

    private void changeEffectStatus(String taskId, MaintenanceEffectStatus next, String reason, String changedBy) {
        MaintenanceEffectStatus previous = effectPlan.currentEffectStatus();
        if (previous == next) {
            return;
        }
        if (previous == MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectStatus", "next", "已生效案件不能回退生效状态");
        }
        LocalDateTime changedAt = LocalDateTime.now();
        AggregateLifecycle.apply(new MaintenanceEffectStatusChangedEvent(id, taskId, previous, next, reason,
                changedAt, changedBy, tenantId));
        if (next == MaintenanceEffectStatus.APPLIED) {
            AggregateLifecycle.apply(new MaintenanceEndorsementIssuedEvent(id, buildEndorsementDocument(changedAt),
                    changedAt));
        }
    }

    /**
     * 组装批单要素：取案件已生效的保全项与字段变更。
     * <p>
     * 「变更后」值以 Policy 生效回执的 {@code appliedFields} 为唯一权威来源（见 {@link #appliedFieldValues()}），
     * 回执未覆盖的字段回落案件拟值——未执行的提案不臆造、已归一化的生效值不丢失。保全项本身不过滤：状态型
     * 保全（如保单中止）无字段明细，但仍是本次批改的内容。
     * </p>
     * <p>
     * 批单号同取自 Policy 回执（见 {@link #appliedEndorsementNo()}）——本域<b>不生成</b>批单号，仅搬运
     * Policy 台账的权威值。
     * </p>
     */
    private EndorsementDocument buildEndorsementDocument(LocalDateTime completedAt) {
        return EndorsementDocument.of(id.id(), policyId.id(), maintenanceType.getCode(), itemInstances,
                appliedFieldValues(), appliedEndorsementNo(), specificEffectiveDate, completedAt, tenantId);
    }

    /**
     * 取本案的权威批单号：Policy 生效回执携带的批改文书号。
     * <p>
     * 🔴 <b>唯一事实源是 Policy 台账</b>：批单号由 policy 域在保单批改落账时生成（保单应用出口
     * {@code PolicyMaintenanceHashing.stableEndorsementNo}），随回执回到本域。本域<b>不得</b>自行生成或补算
     * ——自造一个号会让批单文书与保单台账的批改记录无法对上。
     * </p>
     * <p>
     * <b>为何可为空</b>：案件五条生效出口中只有「保单交易出口」产生批单号，投资账户转换 / 保单贷款放款 /
     * 保单贷款还款 / 保单补发四条出口的回执类型本身不含该编号（属业务事实，非数据缺失）。
     * </p>
     * <p>
     * <b>多任务取值</b>：案件级回执对全部生效任务写入<b>同一份</b>证据（见
     * {@code handle(RecordMaintenanceCasePolicyApplicationCommand)}），故各任务取值恒等；此处按「须唯一」
     * 收敛并显式拒绝歧义，避免将来出现按任务分号时静默择一。
     * </p>
     *
     * @return Policy 台账批单号；案件未走保单交易出口时为 {@code null}
     * @throws MaintenanceValidationException 多个生效任务回执携带不同批单号（事实不自洽）
     */
    private String appliedEndorsementNo() {
        if (workflow == null) {
            return null;
        }
        Set<String> endorsementNos = workflow.getWorkflowTasks().stream()
                .map(MaintenanceWorkflowTask::effectEvidence)
                .filter(Objects::nonNull)
                .map(MaintenanceEffectEvidence::application)
                .filter(Objects::nonNull)
                .map(MaintenancePolicyApplicationEvidence::endorsementNo)
                .filter(no -> no != null && !no.isBlank())
                .collect(Collectors.toSet());
        if (endorsementNos.size() > 1) {
            throw new MaintenanceValidationException(
                    "EndorsementDocument", "endorsementNo", "同一案件各生效任务回执携带不同批单号");
        }
        return endorsementNos.isEmpty() ? null : endorsementNos.iterator().next();
    }

    /**
     * 汇集全部生效任务回执中的权威实际生效值。
     * <p>
     * 🔴 <b>为何取回执而不取 {@code MaintenanceFieldChange.appliedValue}</b>：后者由
     * {@code MaintenanceFieldChange.markApplied} 写入，而该方法在本域<b>无生产调用点</b>（仅单测覆盖），
     * 事件流中该字段恒为 {@code null}。Policy 回执的 {@code appliedFields} 才是实际生效值的唯一权威来源，
     * 故批单要素从任务证据取「变更后」值。
     * </p>
     *
     * @return 键由 {@link EndorsementDocument#fieldKey} 构造的实际生效值索引
     */
    private Map<String, String> appliedFieldValues() {
        if (workflow == null) {
            return Map.of();
        }
        return workflow.getWorkflowTasks().stream()
                .map(MaintenanceWorkflowTask::effectEvidence)
                .filter(Objects::nonNull)
                .map(MaintenanceEffectEvidence::application)
                .filter(Objects::nonNull)
                .flatMap(application -> application.appliedFields().stream())
                .collect(Collectors.toMap(
                        field -> EndorsementDocument.fieldKey(field.itemCode(), field.objectId(), field.fieldCode()),
                        MaintenanceAppliedFieldEvidence::canonicalValue,
                        (existing, replacement) -> replacement));
    }

    /** 校验凭证模板编码取自本保全项冻结配置的输出规则。 */
    private void requireDocumentTemplate(MaintenanceWorkflowTask task, MaintenanceDocumentEvidence evidence) {
        String expected = findItem(task.itemCode()).controls().outputRule().voucherTemplateCode();
        if (expected == null || !expected.equals(evidence.templateCode())) {
            throw new MaintenanceValidationException("RecordMaintenanceDocumentCommand", "templateCode",
                    "凭证模板必须使用冻结配置的输出模板");
        }
    }

    /**
     * 判定该保全类型是否必须先在 Product/Billing 侧登记余额事实才能执行。
     * <p>
     * 🔴 减额缴清（{@code REDUCED_PAID_UP}）<b>不在此列</b>：它的资金动作是「既有现金价值直接转为趸缴保费」，
     * 现金价值与趸缴净费率都取自保单域与产品域的既有事实（见 g04-05 出口），<b>不产生任何收付</b>——
     * 既无应收（加费）也无应付（退费），故无 Product 差额、无 Billing 余额事实可登记。若把它纳入本判据，
     * {@link ExecuteMaintenanceCommand} 处理器会因 {@code hasRecordedBalanceFact()} 恒假而拒绝执行，
     * 使减额缴清案件<b>结构性无法生效</b>。
     * </p>
     * <p>
     * 与「减保」的界限：{@code REDUCTION_PAYMENT}（减额缴费）与 {@code PARTIAL_WITHDRAWAL}（部分领取）
     * 均有退费/支取资金流向保单持有人，故仍在列。
     * </p>
     */
    private boolean requiresPremiumSettlement() {
        return switch (maintenanceType) {
            case ADDITIONAL_PAYMENT, REDUCTION_PAYMENT, POLICY_TERMINATION, POLICY_PERIOD_CHANGE,
                    COVERAGE_AMOUNT_CHANGE, COVERAGE_CHANGE, TOP_UP, PARTIAL_WITHDRAWAL ->
                true;
            default -> false;
        };
    }

    /**
     * 校验命令携带的租户与案件归属租户一致。
     * <p>
     * 租户缺失、空白或不符一律以「保全不存在」拒绝——不区分「不存在」与「不属于本租户」，避免跨租户探测。
     * 这是应用层读模型校验之外的<b>纵深防线</b>：不依赖最终一致的读模型，且对新增派发路径（Saga、编排器
     * 直发命令）自动生效——只要命令经本聚合处理，归属校验就必然发生。
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
