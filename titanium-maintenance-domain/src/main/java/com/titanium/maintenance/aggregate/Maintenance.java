package com.titanium.maintenance.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.common.domain.BaseAggregate;
import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.AddMaintenanceItemCommand;
import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.command.ClaimMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.CompleteMaintenanceCaseInitializationCommand;
import com.titanium.maintenance.command.CompleteMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.CompleteMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.ConfigureMaintenanceItemWithdrawalRecoveryCommand;
import com.titanium.maintenance.command.CreateMaintenanceCaseCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.command.DecideMaintenanceReviewCommand;
import com.titanium.maintenance.command.DecideMaintenanceUnderwritingCommand;
import com.titanium.maintenance.command.DecideMaintenanceWorkflowConditionCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.FailMaintenanceEffectCommand;
import com.titanium.maintenance.command.FailMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.command.FailMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.FailMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.InitializeMaintenanceWorkflowCommand;
import com.titanium.maintenance.command.PauseMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleAttemptCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleFailureCommand;
import com.titanium.maintenance.command.RecordMaintenanceFieldChangesCommand;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenanceItemWithdrawalCompensationCommand;
import com.titanium.maintenance.command.RecordMaintenancePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumQuoteCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenanceRetroactiveProductRecalculationCommand;
import com.titanium.maintenance.command.RecordMaintenanceSurrenderValueCommand;
import com.titanium.maintenance.command.RefreshMaintenanceFieldConflictsCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RequestMaintenanceEffectCommand;
import com.titanium.maintenance.command.ResolveMaintenanceFieldConflictCommand;
import com.titanium.maintenance.command.ResumeMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.RetryMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.command.StartMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.StartMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
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
import com.titanium.maintenance.exception.MaintenanceStatusException;
import com.titanium.maintenance.service.MaintenanceFieldConflictPlanner;
import com.titanium.maintenance.service.MaintenanceFieldProposalPlanner;
import com.titanium.maintenance.service.MaintenanceWorkflowPlanner;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceChange;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposalPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotSet;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalRecoveryContext;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Aggregate
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Maintenance extends BaseAggregate {
    @AggregateIdentifier
    private MaintenanceId                                id;
    private PolicyId                                     policyId;
    private CustomerId                                   customerId;
    private MaintenanceType                              maintenanceType;
    private MaintenanceStatus                            status;
    private EffectiveTimeType                            effectiveTimeType;
    private LocalDateTime                                specificEffectiveDate;
    private BigDecimal                                   totalAmount;
    private BigDecimal                                   refundAmount;
    private MaintenancePremiumSettlementStatus           premiumSettlementStatus;
    private MaintenanceEffectStatus                      effectStatus;
    private MaintenanceEffectSchedule                    effectSchedule;
    private MaintenanceEffectCompensationEvidence        effectCompensationEvidence;
    private MaintenanceRetroactiveImpactAnalysis         retroactiveImpactAnalysis;
    private MaintenanceRetroactivePeriodRecalculation    retroactivePeriodRecalculation;
    private MaintenanceRetroactivePeriodResolution       retroactivePeriodResolution;
    private boolean                                      effectCompensationRequired;
    private String                                       originalCalculationId;
    private String                                       replacementCalculationId;
    private String                                       premiumAdjustmentId;
    private String                                       premiumAdjustmentResultHash;
    private String                                       billingPostingId;
    private String                                       refundInstructionId;
    private String                                       refundOrderId;
    private String                                       refundStatus;
    private Integer                                      commissionAdjustmentCount;
    private MaintenanceBalanceDirection                  balanceDirection;
    private BigDecimal                                   balanceAmount;
    private String                                       balanceCurrency;
    private String                                       surrenderPolicyCode;
    private String                                       surrenderPolicyVersion;
    private String                                       surrenderPolicyContentHash;
    private Integer                                      surrenderPolicyYear;
    private Integer                                      coolingOffDays;
    private String                                       surrenderRefundType;
    private Boolean                                      withinCoolingOff;
    private BigDecimal                                   cashValueRate;
    private BigDecimal                                   retainedCustomerAmount;
    private BigDecimal                                   internalCostRetentionRate;
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
    private List<MaintenanceWorkflowTask>                workflowTasks;
    private Map<String, String>                          workflowOperationHashes;
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
        this.totalAmount = BigDecimal.ZERO;
        this.refundAmount = BigDecimal.ZERO;
        this.premiumSettlementStatus = MaintenancePremiumSettlementStatus.NOT_STARTED;
        this.effectStatus = MaintenanceEffectStatus.NOT_STARTED;
        this.commissionAdjustmentCount = 0;
        this.balanceAmount = BigDecimal.ZERO;
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
        this.workflowTasks = new ArrayList<>();
        this.workflowOperationHashes = new HashMap<>();
        this.createTime = event.createdAt();
        this.createdBy = event.createdBy();
        this.updateTime = event.createdAt();
        this.updatedBy = event.createdBy();
        this.tenantId = event.tenantId();
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
        AggregateLifecycle.apply(new MaintenanceChangeAddedEvent(command.id(), command.changeType(),
                command.fieldName(), command.oldValue(), command.newValue(), LocalDateTime.now(), command.createdBy(),
                this.tenantId));
    }

    /** 向草稿案件添加配置版本已冻结的保全项。 */
    @CommandHandler
    public void handle(AddMaintenanceItemCommand command) {
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
        initializeWorkflow(command.completedBy());
    }

    /** 为已完成项目冻结的历史案件幂等补录流程任务。 */
    @CommandHandler
    public void handle(InitializeMaintenanceWorkflowCommand command) {
        if (!initializationCompleted) {
            throw new MaintenanceValidationException("InitializeMaintenanceWorkflowCommand", "id", "案件初始化完成后才能创建流程任务");
        }
        initializeWorkflow(command.initializedBy());
    }

    @EventSourcingHandler
    public void on(MaintenanceWorkflowInitializedEvent event) {
        this.workflowTasks = new ArrayList<>(event.tasks());
        if (this.workflowOperationHashes == null) {
            this.workflowOperationHashes = new HashMap<>();
        }
        this.updateTime = event.initializedAt();
        this.updatedBy = event.initializedBy();
    }

    /** 领取可处理任务。 */
    @CommandHandler
    public void handle(ClaimMaintenanceWorkflowTaskCommand command) {
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
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.START, command.taskId(), null, null, null, null, command.operatorId());
        transition(command.taskId(), operation, task -> task.start(operation), false);
    }

    /** 完成信息录入或业务校验任务。 */
    @CommandHandler
    public void handle(CompleteMaintenanceWorkflowTaskCommand command) {
        MaintenanceWorkflowTask task = findWorkflowTask(command.taskId());
        if (task.stepType() == MaintenanceStepType.DATA_ENTRY
                && findItem(task.itemCode()).fieldChanges().isEmpty()) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceWorkflowTaskCommand", "fieldChanges", "信息录入至少需要一项实际字段变更");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.COMPLETE, command.taskId(), command.evidenceVersion(), command.evidenceHash(),
                command.resultCode(), command.reason(), command.operatorId());
        transition(command.taskId(), operation, currentTask -> currentTask.complete(operation), true);
    }

    /** 将处理中任务记为失败。 */
    @CommandHandler
    public void handle(FailMaintenanceWorkflowTaskCommand command) {
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.FAIL, command.taskId(), null, null, command.failureCode(),
                command.failureReason(), command.operatorId());
        transition(command.taskId(), operation, task -> task.fail(operation), false);
    }

    /** 将失败任务恢复为可领取状态。 */
    @CommandHandler
    public void handle(RetryMaintenanceWorkflowTaskCommand command) {
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.RETRY, command.taskId(), null, null, null, command.reason(),
                command.operatorId());
        transition(command.taskId(), operation, task -> task.retry(operation), false);
    }

    /** 记录条件规则结论。 */
    @CommandHandler
    public void handle(DecideMaintenanceWorkflowConditionCommand command) {
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
            AggregateLifecycle.apply(new MaintenanceCaseRejectedByReviewEvent(id, command.taskId(),
                    evidence.contentHash(), evidence.policyCode(), evidence.policyVersion(), evidence.comment(),
                    operation.operatedAt(), command.operatorId(), tenantId));
        }
    }

    /** 使用 Underwriting 权威证据决定核保任务，不接受调用方自报结论。 */
    @CommandHandler
    public void handle(DecideMaintenanceUnderwritingCommand command) {
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
                    new MaintenanceCaseRejectedByUnderwritingEvent(id, command.taskId(), evidence.underwritingCaseId(),
                            evidence.contentHash(), evidence.ruleVersion(), evidence.modelVersion(), evidence.summary(),
                            operation.operatedAt(), command.operatorId(), tenantId));
        }
    }

    /** 使用 Product 权威报价或配置无需报价结论更新费用任务，不提前激活生效步骤。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumQuoteCommand command) {
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
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.FAIL_PREMIUM_SETTLEMENT, command.taskId(), null, null, command.failureCode(),
                command.failureReason(), command.operatorId());
        transition(command.taskId(), operation, task -> task.failPremiumSettlement(operation), false);
    }

    /** 为案件建立未来生效计划；此时不冻结 Policy 请求证据。 */
    @CommandHandler
    public void handle(ScheduleMaintenanceEffectCommand command) {
        if (!initializationCompleted || workflowTasks == null || activeEffectTaskIds().isEmpty()) {
            throw new MaintenanceValidationException(
                    "ScheduleMaintenanceEffectCommand", "workflow", "案件流程尚未完成初始化或缺少生效任务");
        }
        LocalDateTime scheduledAt = LocalDateTime.now();
        MaintenanceEffectSchedule candidate = MaintenanceEffectSchedule.create(
                command.scheduleId(), effectiveTimeType, command.tenantZoneId(),
                command.nextExecutionAt(), scheduledAt);
        if (effectSchedule != null) {
            if (effectSchedule.samePlan(
                    candidate.scheduleId(), candidate.tenantZoneId(), candidate.nextExecutionAt())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "ScheduleMaintenanceEffectCommand", "schedule", "案件已存在不同的未来生效计划");
        }
        AggregateLifecycle.apply(new MaintenanceEffectScheduledEvent(id, candidate, command.operatorId(), tenantId));
        changeEffectStatus(activeEffectTaskIds().getFirst(), MaintenanceEffectStatus.SCHEDULED,
                "未来生效计划已建立", command.operatorId());
    }

    /** 暂停尚未完成的未来生效计划。 */
    @CommandHandler
    public void handle(PauseMaintenanceEffectScheduleCommand command) {
        requireSchedule(command.scheduleId());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "PauseMaintenanceEffectScheduleCommand", "reason", "暂停原因不能为空");
        }
        if (effectSchedule.status() == MaintenanceEffectScheduleStatus.PAUSED) {
            return;
        }
        LocalDateTime pausedAt = LocalDateTime.now();
        effectSchedule.pause(pausedAt);
        AggregateLifecycle.apply(new MaintenanceEffectSchedulePausedEvent(
                id, effectSchedule.scheduleId(), command.reason().trim(), pausedAt,
                command.operatorId(), tenantId));
    }

    /** 恢复暂停或失败计划，并将可重试的生效任务恢复到 READY。 */
    @CommandHandler
    public void handle(ResumeMaintenanceEffectScheduleCommand command) {
        requireSchedule(command.scheduleId());
        if (command.nextExecutionAt() == null || command.reason() == null || command.reason().isBlank()) {
            throw new MaintenanceValidationException(
                    "ResumeMaintenanceEffectScheduleCommand", "resume", "恢复时间和原因不能为空");
        }
        if (effectSchedule.status() == MaintenanceEffectScheduleStatus.ACTIVE) {
            return;
        }
        LocalDateTime resumedAt = LocalDateTime.now();
        effectSchedule.resume(command.nextExecutionAt(), resumedAt);
        retryFailedEffectTasks(command.operationId(), command.reason(), command.operatorId());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleResumedEvent(
                id, effectSchedule.scheduleId(), command.nextExecutionAt(), command.reason().trim(),
                resumedAt, command.operatorId(), tenantId));
        changeEffectStatus(activeEffectTaskIds().getFirst(), MaintenanceEffectStatus.SCHEDULED,
                "未来生效计划已恢复", command.operatorId());
    }

    /** 记录一次持有租约的计划执行尝试。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectScheduleAttemptCommand command) {
        requireSchedule(command.scheduleId());
        if (command.attemptId() != null && command.attemptId().equals(effectSchedule.lastAttemptId())) {
            return;
        }
        MaintenanceEffectSchedule attempted = effectSchedule.recordAttempt(
                command.attemptId(), command.attemptedAt());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleAttemptedEvent(
                id, attempted.scheduleId(), attempted.lastAttemptId(), attempted.attemptCount(),
                attempted.lastAttemptAt(), command.operatorId(), tenantId));
    }

    /** 记录计划失败；可重试失败同时恢复生效任务并继续保持计划状态。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectScheduleFailureCommand command) {
        requireSchedule(command.scheduleId());
        if (currentEffectStatus() == MaintenanceEffectStatus.APPLIED) {
            return;
        }
        LocalDateTime failedAt = LocalDateTime.now();
        effectSchedule.recordFailure(
                command.attemptId(), command.errorCode(), command.errorMessage(),
                command.retryAt(), command.terminal(), failedAt);
        if (!command.terminal()) {
            retryFailedEffectTasks(command.attemptId() + ":retry", command.errorMessage(), command.operatorId());
        }
        AggregateLifecycle.apply(new MaintenanceEffectScheduleFailedEvent(
                id, effectSchedule.scheduleId(), command.attemptId(), command.errorCode(),
                command.errorMessage(), command.retryAt(), command.terminal(), failedAt,
                command.operatorId(), tenantId));
        changeEffectStatus(activeEffectTaskIds().getFirst(),
                command.terminal() ? MaintenanceEffectStatus.FAILED : MaintenanceEffectStatus.SCHEDULED,
                command.errorMessage(), command.operatorId());
    }

    /** Policy 权威回执已记录后关闭计划。 */
    @CommandHandler
    public void handle(CompleteMaintenanceEffectScheduleCommand command) {
        requireSchedule(command.scheduleId());
        if (effectSchedule.status() == MaintenanceEffectScheduleStatus.COMPLETED) {
            if (effectSchedule.lastAttemptId().equals(command.attemptId())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceEffectScheduleCommand", "attemptId", "已完成计划的执行标识不匹配");
        }
        if (currentEffectStatus() != MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceEffectScheduleCommand", "effectStatus", "案件尚未取得 Policy 权威成功回执");
        }
        effectSchedule.complete(command.attemptId(), command.completedAt());
        AggregateLifecycle.apply(new MaintenanceEffectScheduleCompletedEvent(
                id, effectSchedule.scheduleId(), command.attemptId(), command.completedAt(),
                command.operatorId(), tenantId));
    }

    /** 冻结一次追溯影响分析范围；新操作会形成递增版本。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactiveImpactAnalysisCommand command) {
        if (!initializationCompleted || effectiveTimeType != EffectiveTimeType.RETROACTIVE
                || specificEffectiveDate == null) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "case", "只有已初始化的追溯案件可以分析影响");
        }
        if (!specificEffectiveDate.equals(command.scopeFrom())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "scopeFrom", "分析起点必须等于案件追溯生效时间");
        }
        if (retroactiveImpactAnalysis != null
                && retroactiveImpactAnalysis.operationId().equals(command.operationId())) {
            if (retroactiveImpactAnalysis.sameStartRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "operationId", "同一操作标识的分析请求不一致");
        }
        if (retroactiveImpactAnalysis != null
                && retroactiveImpactAnalysis.status() == MaintenanceRetroactiveImpactAnalysisStatus.ANALYZING) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactiveImpactAnalysisCommand", "status", "已有追溯影响分析正在执行");
        }
        int nextVersion = retroactiveImpactAnalysis == null ? 1 : retroactiveImpactAnalysis.analysisVersion() + 1;
        MaintenanceRetroactiveImpactAnalysis analysis = MaintenanceRetroactiveImpactAnalysis.start(
                command.analysisId(), nextVersion, command.operationId(), command.requestHash(),
                command.scopeFrom(), command.scopeTo(), command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisStartedEvent(
                id, analysis, command.operatorId(), tenantId));
    }

    /** 保存全部权威域的结构化影响清单；完成分析不改变案件生效状态。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactiveImpactAnalysisCommand command) {
        requireRetroactiveImpactAnalysis(command.analysisId(), command.operationId());
        MaintenanceRetroactiveImpactAnalysis completed = retroactiveImpactAnalysis.complete(
                command.coveredDomains(), command.items(), command.evidenceVersion(),
                command.resultHash(), command.completedAt());
        if (completed == retroactiveImpactAnalysis) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisCompletedEvent(
                id, completed, command.operatorId(), tenantId));
    }

    /** 权威取证失败时记录可重试失败事实。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactiveImpactAnalysisCommand command) {
        requireRetroactiveImpactAnalysis(command.analysisId(), command.operationId());
        MaintenanceRetroactiveImpactAnalysis failed = retroactiveImpactAnalysis.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (failed == retroactiveImpactAnalysis) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveImpactAnalysisFailedEvent(
                id, failed, command.operatorId(), tenantId));
    }

    /** 冻结影响分析版本及本次 Product/Billing 期间重算请求。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactivePeriodRecalculationCommand command) {
        if (!initializationCompleted || effectiveTimeType != EffectiveTimeType.RETROACTIVE
                || retroactiveImpactAnalysis == null
                || retroactiveImpactAnalysis.status() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "analysis", "必须先完成追溯影响分析");
        }
        if (!retroactiveImpactAnalysis.analysisId().equals(command.analysisId())
                || retroactiveImpactAnalysis.analysisVersion() != command.analysisVersion()
                || !retroactiveImpactAnalysis.resultHash().equals(command.analysisResultHash())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "analysis", "影响分析版本或摘要已经漂移");
        }
        if (retroactivePeriodRecalculation != null
                && retroactivePeriodRecalculation.operationId().equals(command.operationId())) {
            if (retroactivePeriodRecalculation.sameRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "operationId", "同一操作标识的重算请求不一致");
        }
        if (retroactivePeriodRecalculation != null
                && retroactivePeriodRecalculation.status()
                        != MaintenanceRetroactivePeriodRecalculationStatus.FAILED
                && !retroactivePeriodRecalculation.terminal()) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodRecalculationCommand", "status", "已有追溯期间重算正在执行");
        }
        int nextVersion = retroactivePeriodRecalculation == null
                ? 1 : retroactivePeriodRecalculation.periodRecalculationVersion() + 1;
        MaintenanceRetroactivePeriodRecalculation recalculation =
                MaintenanceRetroactivePeriodRecalculation.start(
                        command.periodRecalculationId(), nextVersion, command.operationId(), command.requestHash(),
                        command.analysisId(), command.analysisVersion(), command.analysisResultHash(),
                        command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationStartedEvent(
                id, recalculation, command.operatorId(), tenantId));
    }

    /** 保存 Product 期间重算权威检查点，Billing 失败后重试可复用。 */
    @CommandHandler
    public void handle(RecordMaintenanceRetroactiveProductRecalculationCommand command) {
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.recordProduct(
                command.evidence(), command.recordedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactiveProductRecalculationRecordedEvent(
                id, recalculation, command.operatorId(), tenantId));
    }

    /** 保存 Billing 期间调整或关闭期间复核事实。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactivePeriodRecalculationCommand command) {
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.completeBilling(
                command.evidence(), command.completedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationCompletedEvent(
                id, recalculation, command.operatorId(), tenantId));
    }

    /** 保存重算失败事实，同时保留已经成功的 Product 检查点。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactivePeriodRecalculationCommand command) {
        requireRetroactivePeriodRecalculation(command.periodRecalculationId(), command.operationId());
        MaintenanceRetroactivePeriodRecalculation recalculation = retroactivePeriodRecalculation.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (recalculation == retroactivePeriodRecalculation) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodRecalculationFailedEvent(
                id, recalculation, command.operatorId(), tenantId));
    }

    /** 冻结关闭会计期间处理请求，只允许处理当前 Billing 复核批次。 */
    @CommandHandler
    public void handle(StartMaintenanceRetroactivePeriodResolutionCommand command) {
        if (retroactivePeriodRecalculation == null
                || retroactivePeriodRecalculation.status()
                        != MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED
                || retroactivePeriodRecalculation.billingEvidence() == null
                || retroactivePeriodRecalculation.billingEvidence().reviewCount() < 1) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "recalculation",
                    "必须先完成存在关闭期间复核项的期间重算");
        }
        var billingEvidence = retroactivePeriodRecalculation.billingEvidence();
        if (!billingEvidence.batchId().equals(command.billingBatchId())
                || !billingEvidence.resultHash().equals(command.sourceBatchResultHash())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "billingBatch",
                    "关闭期间处理请求与当前Billing批次不一致");
        }
        if (retroactivePeriodResolution != null
                && retroactivePeriodResolution.operationId().equals(command.operationId())) {
            if (retroactivePeriodResolution.sameRequest(command.operationId(), command.requestHash())) {
                return;
            }
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "operationId",
                    "同一操作标识的关闭期间处理请求不一致");
        }
        if (retroactivePeriodResolution != null
                && retroactivePeriodResolution.status()
                        != MaintenanceRetroactivePeriodResolutionStatus.FAILED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceRetroactivePeriodResolutionCommand", "status",
                    "已有关闭期间处理正在执行或已经完成");
        }
        MaintenanceRetroactivePeriodResolution resolution = MaintenanceRetroactivePeriodResolution.start(
                command.periodResolutionId(), command.operationId(), command.requestHash(),
                command.billingBatchId(), command.sourceBatchResultHash(), command.targetAccountingPeriod(),
                command.reason(), command.startedAt());
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionStartedEvent(
                id, resolution, command.operatorId(), tenantId));
    }

    /** 保存 Billing 关闭期间处理权威结论。 */
    @CommandHandler
    public void handle(CompleteMaintenanceRetroactivePeriodResolutionCommand command) {
        requireRetroactivePeriodResolution(command.periodResolutionId(), command.operationId());
        if (command.evidence() == null) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceRetroactivePeriodResolutionCommand", "evidence",
                    "Billing关闭期间处理结论不能为空");
        }
        MaintenanceRetroactivePeriodResolution resolution = retroactivePeriodResolution.complete(
                command.evidence(), command.completedAt());
        if (resolution == retroactivePeriodResolution) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionCompletedEvent(
                id, resolution, command.operatorId(), tenantId));
    }

    /** 保存关闭期间处理失败事实，允许同一请求幂等续跑。 */
    @CommandHandler
    public void handle(FailMaintenanceRetroactivePeriodResolutionCommand command) {
        requireRetroactivePeriodResolution(command.periodResolutionId(), command.operationId());
        MaintenanceRetroactivePeriodResolution resolution = retroactivePeriodResolution.fail(
                command.failureCode(), command.failureMessage(), command.failedAt());
        if (resolution == retroactivePeriodResolution) {
            return;
        }
        AggregateLifecycle.apply(new MaintenanceRetroactivePeriodResolutionFailedEvent(
                id, resolution, command.operatorId(), tenantId));
    }

    /** 冻结 Policy 应用请求，只有专用生效命令可以处理 EFFECT 任务。 */
    @CommandHandler
    public void handle(RequestMaintenanceEffectCommand command) {
        requireSingleEffectTask();
        MaintenanceEffectRequestEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RequestMaintenanceEffectCommand", "evidence", "生效请求证据不能为空");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.REQUEST_EFFECT, command.taskId(), evidence.evidenceVersion(),
                evidence.requestPayloadHash(), MaintenanceEffectStatus.EFFECTING.getCode(), null, command.operatorId());
        boolean applied = transition(command.taskId(), operation, task -> task.requestEffect(evidence, operation),
                false);
        if (applied) {
            changeEffectStatus(command.taskId(), MaintenanceEffectStatus.EFFECTING, "Policy 应用请求已冻结",
                    command.operatorId());
        }
    }

    /** 在一个聚合命令事务中冻结案件全部生效任务。 */
    @CommandHandler
    public void handle(RequestMaintenanceCaseEffectCommand command) {
        List<String> taskIds = requireAllEffectTaskIds(command.taskIds());
        MaintenanceEffectRequestEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RequestMaintenanceCaseEffectCommand", "evidence", "生效请求证据不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflowOperation(
                    caseTaskOperationId(command.operationId(), taskId), MaintenanceWorkflowAction.REQUEST_EFFECT,
                    taskId, evidence.evidenceVersion(), evidence.requestPayloadHash(),
                    MaintenanceEffectStatus.EFFECTING.getCode(), null, command.operatorId());
            applied |= transition(taskId, operation, task -> task.requestEffect(evidence, operation), false);
        }
        if (applied) {
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.EFFECTING, "案件级 Policy 应用请求已冻结",
                    command.operatorId());
        }
    }

    /** 勾稽 Policy 权威回执，全部生效任务完成后才结束案件。 */
    @CommandHandler
    public void handle(RecordMaintenancePolicyApplicationCommand command) {
        requireSingleEffectTask();
        MaintenancePolicyApplicationEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenancePolicyApplicationCommand", "evidence",
                    "Policy 应用回执不能为空");
        }
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION, command.taskId(), evidence.evidenceVersion(),
                evidence.applicationHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.endorsementNo(),
                command.operatorId());
        boolean applied = transition(command.taskId(), operation,
                task -> task.recordPolicyApplication(evidence, operation), false);
        if (applied) {
            MaintenanceEffectStatus next = allEffectTasksApplied() ? MaintenanceEffectStatus.APPLIED
                    : MaintenanceEffectStatus.EFFECTING;
            changeEffectStatus(command.taskId(), next, "Policy 权威回执已记录", command.operatorId());
        }
    }

    /** 在一个聚合命令事务中为案件全部生效任务记录同一权威回执。 */
    @CommandHandler
    public void handle(RecordMaintenanceCasePolicyApplicationCommand command) {
        List<String> taskIds = requireAllEffectTaskIds(command.taskIds());
        MaintenancePolicyApplicationEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceCasePolicyApplicationCommand", "evidence",
                    "Policy 应用回执不能为空");
        }
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflowOperation(
                    caseTaskOperationId(command.operationId(), taskId),
                    MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION, taskId, evidence.evidenceVersion(),
                    evidence.applicationHash(), MaintenanceEffectStatus.APPLIED.getCode(), evidence.endorsementNo(),
                    command.operatorId());
            applied |= transition(taskId, operation, task -> task.recordPolicyApplication(evidence, operation), false);
        }
        if (applied) {
            if (!allEffectTasksApplied()) {
                throw new MaintenanceValidationException("RecordMaintenanceCasePolicyApplicationCommand", "taskIds",
                        "案件生效任务未全部完成");
            }
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.APPLIED, "案件级 Policy 权威回执已记录",
                    command.operatorId());
            if (effectCompensationRequired && effectCompensationEvidence != null) {
                AggregateLifecycle.apply(
                        new MaintenanceEffectCompensationResolvedEvent(id, effectCompensationEvidence.compensationId(),
                                evidence.endorsementNo(), LocalDateTime.now(), command.operatorId(), tenantId));
            }
        }
    }

    /** Policy 调用或回执校验失败时形成可恢复失败。 */
    @CommandHandler
    public void handle(FailMaintenanceEffectCommand command) {
        requireSingleEffectTask();
        MaintenanceWorkflowOperation operation = workflowOperation(command.operationId(),
                MaintenanceWorkflowAction.FAIL_EFFECT, command.taskId(), null, null, command.failureCode(),
                command.failureReason(), command.operatorId());
        boolean applied = transition(command.taskId(), operation, task -> task.failEffect(operation), false);
        if (applied) {
            changeEffectStatus(command.taskId(), MaintenanceEffectStatus.FAILED, command.failureReason(),
                    command.operatorId());
        }
    }

    /** 在一个聚合命令事务中将案件全部已发起生效任务置为失败。 */
    @CommandHandler
    public void handle(FailMaintenanceCaseEffectCommand command) {
        List<String> taskIds = requireAllEffectTaskIds(command.taskIds());
        boolean applied = false;
        for (String taskId : taskIds) {
            MaintenanceWorkflowOperation operation = workflowOperation(
                    caseTaskOperationId(command.operationId(), taskId), MaintenanceWorkflowAction.FAIL_EFFECT, taskId,
                    null, null, command.failureCode(), command.failureReason(), command.operatorId());
            applied |= transition(taskId, operation, task -> task.failEffect(operation), false);
        }
        if (applied) {
            changeEffectStatus(taskIds.getFirst(), MaintenanceEffectStatus.FAILED, command.failureReason(),
                    command.operatorId());
        }
    }

    /** Policy 已成功而案件回执写入失败时，记录独立人工补偿事实。 */
    @CommandHandler
    public void handle(RecordMaintenanceEffectCompensationCommand command) {
        MaintenanceEffectCompensationEvidence evidence = command.evidence();
        if (evidence == null) {
            throw new MaintenanceValidationException("RecordMaintenanceEffectCompensationCommand", "evidence",
                    "补偿事实不能为空");
        }
        requireEffectCompensationContext(command.taskId(), evidence);
        if (effectCompensationEvidence != null) {
            if (effectCompensationEvidence.equals(evidence)) {
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
        this.updateTime = event.transitionedAt();
        this.updatedBy = event.operatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectStatusChangedEvent event) {
        this.effectStatus = event.currentStatus();
        if (event.currentStatus() == MaintenanceEffectStatus.APPLIED) {
            this.status = MaintenanceStatus.COMPLETED;
        }
        this.updateTime = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduledEvent event) {
        this.effectSchedule = event.schedule();
        this.updateTime = event.schedule().createdAt();
        this.updatedBy = event.scheduledBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectSchedulePausedEvent event) {
        this.effectSchedule = effectSchedule.pause(event.pausedAt());
        this.updateTime = event.pausedAt();
        this.updatedBy = event.pausedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleResumedEvent event) {
        this.effectSchedule = effectSchedule.resume(event.nextExecutionAt(), event.resumedAt());
        this.updateTime = event.resumedAt();
        this.updatedBy = event.resumedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleAttemptedEvent event) {
        this.effectSchedule = effectSchedule.recordAttempt(event.attemptId(), event.attemptedAt());
        this.updateTime = event.attemptedAt();
        this.updatedBy = event.attemptedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleFailedEvent event) {
        this.effectSchedule = effectSchedule.recordFailure(
                event.attemptId(), event.errorCode(), event.errorMessage(), event.retryAt(),
                event.terminal(), event.failedAt());
        this.updateTime = event.failedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectScheduleCompletedEvent event) {
        this.effectSchedule = effectSchedule.complete(event.attemptId(), event.completedAt());
        this.updateTime = event.completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationRequiredEvent event) {
        this.effectCompensationEvidence = event.evidence();
        this.effectCompensationRequired = true;
        this.updateTime = event.recordedAt();
        this.updatedBy = event.recordedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceEffectCompensationResolvedEvent event) {
        this.effectCompensationRequired = false;
        this.updateTime = event.resolvedAt();
        this.updatedBy = event.resolvedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisStartedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
        this.updateTime = event.analysis().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisCompletedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
        this.updateTime = event.analysis().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveImpactAnalysisFailedEvent event) {
        this.retroactiveImpactAnalysis = event.analysis();
        this.updateTime = event.analysis().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationStartedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
        this.updateTime = event.recalculation().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactiveProductRecalculationRecordedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
        this.updateTime = event.recalculation().updatedAt();
        this.updatedBy = event.recordedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationCompletedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
        this.updateTime = event.recalculation().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodRecalculationFailedEvent event) {
        this.retroactivePeriodRecalculation = event.recalculation();
        this.updateTime = event.recalculation().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionStartedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
        this.updateTime = event.resolution().startedAt();
        this.updatedBy = event.startedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionCompletedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
        this.updateTime = event.resolution().completedAt();
        this.updatedBy = event.completedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceRetroactivePeriodResolutionFailedEvent event) {
        this.retroactivePeriodResolution = event.resolution();
        this.updateTime = event.resolution().completedAt();
        this.updatedBy = event.failedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseRejectedByReviewEvent event) {
        this.status = MaintenanceStatus.REJECTED;
        this.updateTime = event.rejectedAt();
        this.updatedBy = event.rejectedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceCaseRejectedByUnderwritingEvent event) {
        this.status = MaintenanceStatus.REJECTED;
        this.updateTime = event.rejectedAt();
        this.updatedBy = event.rejectedBy();
    }

    /** 冻结项目撤销请求；已发起或已完成 Policy 生效的项目必须改走反向保全。 */
    @CommandHandler
    public MaintenanceItemWithdrawal handle(StartMaintenanceItemWithdrawalCommand command) {
        requireItemStatusMutable("WITHDRAW_MAINTENANCE_ITEM");
        if (!initializationCompleted || !Objects.equals(tenantId, command.tenantId())) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "tenantId", "案件未初始化完成或租户不一致");
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
        if (currentEffectStatus() == MaintenanceEffectStatus.EFFECTING
                || currentEffectStatus() == MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "StartMaintenanceItemWithdrawalCommand", "effectStatus", "Policy 生效已发起，必须新建反向保全案件");
        }
        if (itemTasks(command.itemCode()).stream()
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
        if (!Objects.equals(tenantId, command.tenantId())) {
            throw new MaintenanceValidationException(
                    "ConfigureMaintenanceItemWithdrawalRecoveryCommand", "tenantId", "恢复上下文租户与案件不一致");
        }
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

    /** 保存一个保全项的完整字段变化草稿。 */
    @CommandHandler
    public void handle(RecordMaintenanceFieldChangesCommand command) {
        requireItemEditingAllowed("RECORD_MAINTENANCE_FIELD_CHANGES");
        if (source != null) {
            throw new MaintenanceValidationException("RecordMaintenanceFieldChangesCommand", "changes",
                    "独立建案必须通过权威字段目录提案命令保存字段草稿");
        }
        MaintenanceItemInstance item = findItem(command.itemCode());
        item.withFieldChanges(command.changes());
        AggregateLifecycle.apply(new MaintenanceFieldChangesRecordedEvent(command.id(), command.itemCode(),
                command.changes(), LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    /** 使用当前 Policy 与目录权威证据生成并保存完整字段草稿。 */
    @CommandHandler
    public void handle(ProposeMaintenanceFieldChangesCommand command) {
        requireItemEditingAllowed("PROPOSE_MAINTENANCE_FIELD_CHANGES");
        if (!Objects.equals(tenantId, command.tenantId())) {
            throw new MaintenanceValidationException("ProposeMaintenanceFieldChangesCommand", "tenantId",
                    "字段提案租户与案件不一致");
        }
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
        requireConflictMutable("REFRESH_MAINTENANCE_FIELD_CONFLICTS");
        validateConflictContext(command.currentPolicySnapshot(), command.tenantId());
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
        requireConflictMutable("RESOLVE_MAINTENANCE_FIELD_CONFLICT");
        if (!Objects.equals(tenantId, command.tenantId())) {
            throw new MaintenanceValidationException(
                    "ResolveMaintenanceFieldConflictCommand", "tenantId", "冲突解决租户与案件不一致");
        }
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

    // 处理计算保费命令
    @CommandHandler
    public void handle(CalculateMaintenancePremiumCommand command) {
        if (premiumAdjustmentId != null) {
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(),
                    "CALCULATE_MAINTENANCE_PREMIUM", "已进入结构化生命周期计价后不能再写入人工金额");
        }
        AggregateLifecycle.apply(
                new MaintenancePremiumCalculatedEvent(command.id(), command.totalAmount(), command.refundAmount(),
                        command.calculationDetails(), LocalDateTime.now(), command.updatedBy(), this.tenantId));
    }

    /** 记录 Product 差额检查点；同一事实重放为幂等，不允许一个案件绑定不同差额。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumAdjustmentCommand command) {
        if (premiumAdjustmentId != null) {
            if (sameAdjustment(command)) {
                return;
            }
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "RECORD_PREMIUM_ADJUSTMENT",
                    "同一保全案件不能绑定不同的费用差额事实");
        }
        requireSettlementMutable("RECORD_PREMIUM_ADJUSTMENT");
        validateAdjustment(command);
        AggregateLifecycle.apply(new MaintenancePremiumAdjustmentRecordedEvent(command.id(),
                command.originalCalculationId(), command.replacementCalculationId(), command.adjustmentId(),
                command.adjustmentResultHash(), command.direction(), command.amount(), command.currency(),
                LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    /** 记录退保价值策略证据；只允许保单终止案件绑定已记录的 Product 差额。 */
    @CommandHandler
    public void handle(RecordMaintenanceSurrenderValueCommand command) {
        if (surrenderPolicyContentHash != null) {
            if (sameSurrenderValue(command)) {
                return;
            }
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "RECORD_SURRENDER_VALUE",
                    "同一退保案件不能绑定不同的现金价值策略事实");
        }
        if (maintenanceType != MaintenanceType.POLICY_TERMINATION) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "maintenanceType",
                    "只有保单终止案件可以记录退保价值");
        }
        if (!Objects.equals(premiumAdjustmentId, command.adjustmentId())) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "adjustmentId",
                    "退保价值来源与 Product 差额不一致");
        }
        validateSurrenderValue(command);
        AggregateLifecycle.apply(new MaintenanceSurrenderValueRecordedEvent(command.id(), command.adjustmentId(),
                command.policyCode(), command.policyVersion(), command.policyContentHash(), command.policyYear(),
                command.coolingOffDays(), command.refundType(), command.withinCoolingOff(), command.cashValueRate(),
                command.retainedCustomerAmount(), command.internalCostRetentionRate(), LocalDateTime.now(),
                command.updatedBy(), tenantId));
    }

    /** 记录 Billing 入账检查点；POSTED 仅表示余额事实登记成功，不表示资金已结算。 */
    @CommandHandler
    public void handle(RecordMaintenancePremiumPostingCommand command) {
        if (billingPostingId != null) {
            if (samePosting(command)) {
                return;
            }
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "RECORD_PREMIUM_POSTING",
                    "同一保全案件不能绑定不同的 Billing 入账事实");
        }
        requireSettlementMutable("RECORD_PREMIUM_POSTING");
        if (premiumSettlementStatus != MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED
                || balanceDirection == MaintenanceBalanceDirection.NONE) {
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "RECORD_PREMIUM_POSTING",
                    "当前差额不需要或尚不能登记 Billing 余额事实");
        }
        if (!Objects.equals(premiumAdjustmentId, command.adjustmentId())
                || !Objects.equals(premiumAdjustmentResultHash, command.adjustmentResultHash())) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumPostingCommand", "adjustmentId",
                    "Billing 入账来源与 Product 差额不一致");
        }
        if (command.postingId() == null || command.postingId().isBlank() || !"POSTED".equals(command.postingStatus())) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumPostingCommand", "postingId",
                    "Billing 入账事实无效");
        }
        AggregateLifecycle.apply(new MaintenancePremiumPostingRecordedEvent(command.id(), command.adjustmentId(),
                command.adjustmentResultHash(), command.postingId(), command.postingStatus(), LocalDateTime.now(),
                command.updatedBy(), tenantId));
    }

    /**
     * 记录独立资金结算检查点。CREDIT 可从处理中或失败状态恢复，DEBIT 始终只保留余额已登记语义。
     */
    @CommandHandler
    public void handle(RecordMaintenanceFinancialSettlementCommand command) {
        validateFinancialSettlement(command);
        MaintenancePremiumSettlementStatus targetStatus = financialSettlementStatus(command.refundStatus());
        if (sameFinancialSettlement(command, targetStatus)) {
            return;
        }
        if (premiumSettlementStatus == MaintenancePremiumSettlementStatus.SETTLED) {
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "RECORD_FINANCIAL_SETTLEMENT",
                    "已完成资金结算的保全不能回退结算事实");
        }
        AggregateLifecycle.apply(new MaintenanceFinancialSettlementRecordedEvent(command.id(), command.postingId(),
                command.refundInstructionId(), command.refundOrderId(), command.refundStatus(),
                command.commissionAdjustmentCount(), targetStatus, LocalDateTime.now(), command.updatedBy(), tenantId));
    }

    // 处理执行保全命令
    @CommandHandler
    public void handle(ExecuteMaintenanceCommand command) {
        if (requiresPremiumSettlement() && !hasRecordedBalanceFact()) {
            throw new MaintenanceStatusException(id.id(), premiumSettlementStatus.name(), "EXECUTE_MAINTENANCE",
                    "价格影响型保全必须先完成 Product 差额和 Billing 余额事实登记");
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

    // 处理保费计算事件
    @EventSourcingHandler
    public void on(MaintenancePremiumCalculatedEvent event) {
        this.totalAmount = event.totalAmount();
        this.refundAmount = event.refundAmount();
        this.updateTime = event.updatedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumAdjustmentRecordedEvent event) {
        this.originalCalculationId = event.originalCalculationId();
        this.replacementCalculationId = event.replacementCalculationId();
        this.premiumAdjustmentId = event.adjustmentId();
        this.premiumAdjustmentResultHash = event.adjustmentResultHash();
        this.balanceDirection = event.direction();
        this.balanceAmount = event.amount();
        this.balanceCurrency = event.currency();
        this.totalAmount = event.direction() == MaintenanceBalanceDirection.DEBIT ? event.amount() : BigDecimal.ZERO;
        this.refundAmount = event.direction() == MaintenanceBalanceDirection.CREDIT ? event.amount() : BigDecimal.ZERO;
        this.premiumSettlementStatus = event.direction() == MaintenanceBalanceDirection.NONE
                ? MaintenancePremiumSettlementStatus.NOT_REQUIRED
                : MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED;
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceSurrenderValueRecordedEvent event) {
        this.surrenderPolicyCode = event.policyCode();
        this.surrenderPolicyVersion = event.policyVersion();
        this.surrenderPolicyContentHash = event.policyContentHash();
        this.surrenderPolicyYear = event.policyYear();
        this.coolingOffDays = event.coolingOffDays();
        this.surrenderRefundType = event.refundType();
        this.withinCoolingOff = event.withinCoolingOff();
        this.cashValueRate = event.cashValueRate();
        this.retainedCustomerAmount = event.retainedCustomerAmount();
        this.internalCostRetentionRate = event.internalCostRetentionRate();
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenancePremiumPostingRecordedEvent event) {
        this.billingPostingId = event.postingId();
        this.premiumSettlementStatus = MaintenancePremiumSettlementStatus.POSTED;
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceFinancialSettlementRecordedEvent event) {
        this.refundInstructionId = event.refundInstructionId();
        this.refundOrderId = event.refundOrderId();
        this.refundStatus = event.refundStatus();
        this.commissionAdjustmentCount = event.commissionAdjustmentCount();
        this.premiumSettlementStatus = event.premiumSettlementStatus();
        this.updateTime = event.recordedAt();
        this.updatedBy = event.updatedBy();
    }

    // 处理执行保全事件
    @EventSourcingHandler
    public void on(MaintenanceExecutedEvent event) {
        this.status = MaintenanceStatus.COMPLETED;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getDescription() {
        return description;
    }

    public List<MaintenanceChange> getChanges() {
        return changes;
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

    public MaintenancePremiumSettlementStatus getPremiumSettlementStatus() {
        return premiumSettlementStatus;
    }

    public MaintenanceEffectStatus getEffectStatus() {
        return currentEffectStatus();
    }

    public MaintenanceEffectSchedule getEffectSchedule() {
        return effectSchedule;
    }

    public boolean isEffectCompensationRequired() {
        return effectCompensationRequired;
    }

    public MaintenanceEffectCompensationEvidence getEffectCompensationEvidence() {
        return effectCompensationEvidence;
    }

    public MaintenanceRetroactiveImpactAnalysis getRetroactiveImpactAnalysis() {
        return retroactiveImpactAnalysis;
    }

    public MaintenanceRetroactivePeriodRecalculation getRetroactivePeriodRecalculation() {
        return retroactivePeriodRecalculation;
    }

    public MaintenanceRetroactivePeriodResolution getRetroactivePeriodResolution() {
        return retroactivePeriodResolution;
    }

    public List<MaintenanceWorkflowTask> getWorkflowTasks() {
        return workflowTasks == null ? List.of() : List.copyOf(workflowTasks);
    }

    public String getOriginalCalculationId() {
        return originalCalculationId;
    }

    public String getReplacementCalculationId() {
        return replacementCalculationId;
    }

    public String getPremiumAdjustmentId() {
        return premiumAdjustmentId;
    }

    public String getPremiumAdjustmentResultHash() {
        return premiumAdjustmentResultHash;
    }

    public String getBillingPostingId() {
        return billingPostingId;
    }

    public String getRefundInstructionId() {
        return refundInstructionId;
    }

    public String getRefundOrderId() {
        return refundOrderId;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public Integer getCommissionAdjustmentCount() {
        return commissionAdjustmentCount;
    }

    public MaintenanceBalanceDirection getBalanceDirection() {
        return balanceDirection;
    }

    public BigDecimal getBalanceAmount() {
        return balanceAmount;
    }

    public String getBalanceCurrency() {
        return balanceCurrency;
    }

    public String getSurrenderPolicyCode() {
        return surrenderPolicyCode;
    }

    public String getSurrenderPolicyVersion() {
        return surrenderPolicyVersion;
    }

    public String getSurrenderPolicyContentHash() {
        return surrenderPolicyContentHash;
    }

    public Integer getSurrenderPolicyYear() {
        return surrenderPolicyYear;
    }

    public Integer getCoolingOffDays() {
        return coolingOffDays;
    }

    public Map<String, MaintenanceItemWithdrawal> getItemWithdrawals() {
        return itemWithdrawals == null ? Map.of() : Map.copyOf(itemWithdrawals);
    }

    public Map<String, MaintenanceItemWithdrawalRecoveryContext> getItemWithdrawalRecoveryContexts() {
        return itemWithdrawalRecoveryContexts == null ? Map.of() : Map.copyOf(itemWithdrawalRecoveryContexts);
    }

    public String getSurrenderRefundType() {
        return surrenderRefundType;
    }

    public Boolean getWithinCoolingOff() {
        return withinCoolingOff;
    }

    public BigDecimal getCashValueRate() {
        return cashValueRate;
    }

    public BigDecimal getRetainedCustomerAmount() {
        return retainedCustomerAmount;
    }

    public BigDecimal getInternalCostRetentionRate() {
        return internalCostRetentionRate;
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
        if (currentEffectStatus() == MaintenanceEffectStatus.APPLIED
                || currentEffectStatus() == MaintenanceEffectStatus.EFFECTING) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflict", "effectStatus", "Policy 请求冻结后不能刷新或解决字段冲突");
        }
    }

    private void validateConflictContext(PolicyMaintenanceSnapshot snapshot, String commandTenantId) {
        if (!Objects.equals(tenantId, commandTenantId)) {
            throw new MaintenanceValidationException(
                    "RefreshMaintenanceFieldConflictsCommand", "tenantId", "冲突刷新租户与案件不一致");
        }
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
        MaintenanceEffectStatus current = currentEffectStatus();
        if (plan.conflictCount() > 0) {
            changeEffectStatus(conflictEffectTaskId(), MaintenanceEffectStatus.CONFLICTED,
                    "检测到顺序外字段冲突", changedBy);
            return;
        }
        if (current == MaintenanceEffectStatus.CONFLICTED) {
            MaintenanceEffectStatus restored = effectSchedule != null
                    && effectSchedule.status() != MaintenanceEffectScheduleStatus.COMPLETED
                            ? MaintenanceEffectStatus.SCHEDULED
                            : MaintenanceEffectStatus.NOT_STARTED;
            changeEffectStatus(conflictEffectTaskId(), restored, "字段冲突已全部解决", changedBy);
        }
    }

    private String conflictEffectTaskId() {
        return activeEffectTaskIds().stream().findFirst().orElseThrow(() -> new MaintenanceValidationException(
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

    private List<MaintenanceWorkflowTask> itemTasks(String itemCode) {
        return workflowTasks == null ? List.of() : workflowTasks.stream()
                .filter(task -> task.itemCode().equals(itemCode))
                .toList();
    }

    private MaintenanceWorkflowTask sourceFeeTask(String itemCode) {
        List<MaintenanceWorkflowTask> postedFeeTasks = itemTasks(itemCode).stream()
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
        itemTasks(withdrawal.itemCode()).stream()
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

    private void requireSettlementMutable(String target) {
        if (status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceStatusException(id.id(), status.name(), target, "已完成或已拒绝的保全不能登记费用差额");
        }
    }

    private void initializeWorkflow(String initializedBy) {
        if (workflowTasks != null && !workflowTasks.isEmpty()) {
            return;
        }
        List<MaintenanceWorkflowTask> tasks = new MaintenanceWorkflowPlanner().plan(id, itemInstances);
        AggregateLifecycle.apply(
                new MaintenanceWorkflowInitializedEvent(id, tasks, LocalDateTime.now(), initializedBy, tenantId));
    }

    private void changeEffectStatus(String taskId, MaintenanceEffectStatus next, String reason, String changedBy) {
        MaintenanceEffectStatus previous = currentEffectStatus();
        if (previous == next) {
            return;
        }
        if (previous == MaintenanceEffectStatus.APPLIED) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectStatus", "next", "已生效案件不能回退生效状态");
        }
        AggregateLifecycle.apply(new MaintenanceEffectStatusChangedEvent(id, taskId, previous, next, reason,
                LocalDateTime.now(), changedBy, tenantId));
    }

    private MaintenanceEffectStatus currentEffectStatus() {
        return effectStatus == null ? MaintenanceEffectStatus.NOT_STARTED : effectStatus;
    }

    private List<String> activeEffectTaskIds() {
        return workflowTasks == null ? List.of()
                : workflowTasks.stream()
                        .filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                        .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED)
                        .map(MaintenanceWorkflowTask::taskId)
                        .sorted()
                        .toList();
    }

    private void requireSchedule(String scheduleId) {
        if (effectSchedule == null || scheduleId == null
                || !effectSchedule.scheduleId().equals(scheduleId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "scheduleId", "未来生效计划不存在或不匹配");
        }
    }

    private void requireRetroactiveImpactAnalysis(String analysisId, String operationId) {
        if (retroactiveImpactAnalysis == null || analysisId == null || operationId == null
                || !retroactiveImpactAnalysis.analysisId().equals(analysisId.trim())
                || !retroactiveImpactAnalysis.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactiveImpactAnalysis", "analysisId", "追溯影响分析不存在或操作标识不匹配");
        }
    }

    private void requireRetroactivePeriodRecalculation(String recalculationId, String operationId) {
        if (retroactivePeriodRecalculation == null || recalculationId == null || operationId == null
                || !retroactivePeriodRecalculation.periodRecalculationId().equals(recalculationId.trim())
                || !retroactivePeriodRecalculation.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactivePeriodRecalculation", "periodRecalculationId",
                    "追溯期间重算不存在或操作标识不匹配");
        }
    }

    private void requireRetroactivePeriodResolution(String resolutionId, String operationId) {
        if (retroactivePeriodResolution == null || resolutionId == null || operationId == null
                || !retroactivePeriodResolution.periodResolutionId().equals(resolutionId.trim())
                || !retroactivePeriodResolution.operationId().equals(operationId.trim())) {
            throw new MaintenanceValidationException(
                    "MaintenanceRetroactivePeriodResolution", "periodResolutionId",
                    "关闭会计期间处理不存在或操作标识不匹配");
        }
    }

    private void retryFailedEffectTasks(String operationId, String reason, String operatorId) {
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

    private boolean allEffectTasksApplied() {
        List<MaintenanceWorkflowTask> effectTasks = workflowTasks.stream()
                .filter(task -> task.stepType() == MaintenanceStepType.EFFECT).toList();
        return !effectTasks.isEmpty()
                && effectTasks.stream().allMatch(task -> task.status() == MaintenanceWorkflowTaskStatus.COMPLETED
                        || task.status() == MaintenanceWorkflowTaskStatus.SKIPPED);
    }

    private void requireSingleEffectTask() {
        long effectTaskCount = workflowTasks == null ? 0
                : workflowTasks.stream().filter(task -> task.stepType() == MaintenanceStepType.EFFECT)
                        .filter(task -> task.status() != MaintenanceWorkflowTaskStatus.SKIPPED).count();
        if (effectTaskCount != 1) {
            throw new MaintenanceValidationException("MaintenanceEffect", "taskIds", "多项目案件必须使用案件级原子生效命令");
        }
    }

    private List<String> requireAllEffectTaskIds(List<String> taskIds) {
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

    private void requireEffectCompensationContext(
            String taskId, MaintenanceEffectCompensationEvidence evidence) {
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

    private String caseTaskOperationId(String operationId, String taskId) {
        if (operationId == null || operationId.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceCaseEffect", "operationId", "案件级生效操作号不能为空");
        }
        return operationId.trim() + ":" + taskId;
    }

    private boolean transition(String taskId, MaintenanceWorkflowOperation operation,
                               UnaryOperator<MaintenanceWorkflowTask> transition, boolean activateNext) {
        if (workflowOperationAlreadyApplied(operation)) {
            return false;
        }
        requireWorkflowMutable(operation.action().getCode());
        MaintenanceWorkflowTask beforeTask = findWorkflowTask(taskId);
        MaintenanceWorkflowTask afterTask = transition.apply(beforeTask);
        MaintenanceWorkflowTask activatedBefore = null;
        MaintenanceWorkflowTask activatedAfter = null;
        if (activateNext) {
            activatedBefore = findNextPendingTask(beforeTask);
            if (activatedBefore != null) {
                activatedAfter = activatedBefore.activate(operation);
            }
        }
        AggregateLifecycle.apply(new MaintenanceWorkflowTaskTransitionedEvent(id, beforeTask, afterTask,
                activatedBefore, activatedAfter, operation.operationId(), operation.payloadHash(),
                operation.operatedAt(), operation.operatedBy(), tenantId));
        return true;
    }

    private MaintenanceWorkflowOperation workflowOperation(String operationId, MaintenanceWorkflowAction action,
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

    private void requireWorkflowMutable(String target) {
        requireItemStatusMutable(target);
        if (!initializationCompleted || workflowTasks == null || workflowTasks.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceWorkflowTask", "workflowTasks", "案件流程任务尚未初始化");
        }
    }

    private MaintenanceWorkflowTask findWorkflowTask(String taskId) {
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

    private void validateAdjustment(RecordMaintenancePremiumAdjustmentCommand command) {
        if (command.originalCalculationId() == null || command.originalCalculationId().isBlank()
                || command.replacementCalculationId() == null || command.replacementCalculationId().isBlank()
                || command.adjustmentId() == null || command.adjustmentId().isBlank()
                || command.adjustmentResultHash() == null || command.adjustmentResultHash().isBlank()
                || command.direction() == null || command.amount() == null || command.currency() == null
                || command.currency().isBlank()) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumAdjustmentCommand", "Product 差额事实字段不完整");
        }
        if (command.amount().signum() < 0
                || (command.direction() == MaintenanceBalanceDirection.NONE && command.amount().signum() != 0)
                || (command.direction() != MaintenanceBalanceDirection.NONE && command.amount().signum() <= 0)) {
            throw new MaintenanceValidationException("RecordMaintenancePremiumAdjustmentCommand", "amount",
                    "余额方向与差额金额不一致");
        }
    }

    private boolean sameAdjustment(RecordMaintenancePremiumAdjustmentCommand command) {
        return Objects.equals(originalCalculationId, command.originalCalculationId())
                && Objects.equals(replacementCalculationId, command.replacementCalculationId())
                && Objects.equals(premiumAdjustmentId, command.adjustmentId())
                && Objects.equals(premiumAdjustmentResultHash, command.adjustmentResultHash())
                && balanceDirection == command.direction() && balanceAmount.compareTo(command.amount()) == 0
                && Objects.equals(balanceCurrency, command.currency());
    }

    private boolean samePosting(RecordMaintenancePremiumPostingCommand command) {
        return Objects.equals(premiumAdjustmentId, command.adjustmentId())
                && Objects.equals(premiumAdjustmentResultHash, command.adjustmentResultHash())
                && Objects.equals(billingPostingId, command.postingId()) && "POSTED".equals(command.postingStatus());
    }

    private void validateSurrenderValue(RecordMaintenanceSurrenderValueCommand command) {
        if (!hasText(command.policyCode()) || !hasText(command.policyVersion()) || !hasText(command.policyContentHash())
                || command.policyContentHash().length() != 64 || command.policyYear() == null
                || command.policyYear() < 1 || command.coolingOffDays() == null || command.coolingOffDays() < 0
                || !hasText(command.refundType()) || command.withinCoolingOff() == null
                || invalidRate(command.cashValueRate()) || invalidRate(command.internalCostRetentionRate())
                || command.retainedCustomerAmount() == null || command.retainedCustomerAmount().signum() < 0) {
            throw new MaintenanceValidationException("RecordMaintenanceSurrenderValueCommand", "Product 退保价值策略事实不完整");
        }
    }

    private boolean sameSurrenderValue(RecordMaintenanceSurrenderValueCommand command) {
        return Objects.equals(premiumAdjustmentId, command.adjustmentId())
                && Objects.equals(surrenderPolicyCode, command.policyCode())
                && Objects.equals(surrenderPolicyVersion, command.policyVersion())
                && Objects.equals(surrenderPolicyContentHash, command.policyContentHash())
                && Objects.equals(surrenderPolicyYear, command.policyYear())
                && Objects.equals(coolingOffDays, command.coolingOffDays())
                && Objects.equals(surrenderRefundType, command.refundType())
                && Objects.equals(withinCoolingOff, command.withinCoolingOff())
                && cashValueRate.compareTo(command.cashValueRate()) == 0
                && retainedCustomerAmount.compareTo(command.retainedCustomerAmount()) == 0
                && internalCostRetentionRate.compareTo(command.internalCostRetentionRate()) == 0;
    }

    private boolean invalidRate(BigDecimal value) {
        return value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }

    private void validateFinancialSettlement(RecordMaintenanceFinancialSettlementCommand command) {
        if (billingPostingId == null || !Objects.equals(billingPostingId, command.postingId())) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "postingId",
                    "资金结算来源与 Billing 入账不一致");
        }
        if (command.commissionAdjustmentCount() == null || command.commissionAdjustmentCount() < 0) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand",
                    "commissionAdjustmentCount", "佣金调整数量不能为负数");
        }
        boolean hasInstruction = hasText(command.refundInstructionId());
        boolean hasOrder = hasText(command.refundOrderId());
        boolean hasRefundStatus = hasText(command.refundStatus());
        if (hasOrder && !hasInstruction) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundOrderId",
                    "退款订单必须关联退款指令");
        }
        if ((hasInstruction || hasOrder) && !hasRefundStatus) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "退款事实必须包含退款状态");
        }
        if (balanceDirection == MaintenanceBalanceDirection.CREDIT && !hasRefundStatus) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "退费资金事实必须包含退款状态");
        }
        if (balanceDirection == MaintenanceBalanceDirection.DEBIT
                && (hasInstruction || hasOrder || !"NOT_REQUIRED".equals(command.refundStatus()))) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "追加应收不得包含退款事实");
        }
        if (balanceDirection == MaintenanceBalanceDirection.NONE) {
            throw new MaintenanceValidationException("RecordMaintenanceFinancialSettlementCommand", "refundStatus",
                    "无余额影响时不得记录资金结算");
        }
    }

    private MaintenancePremiumSettlementStatus financialSettlementStatus(String currentRefundStatus) {
        if (balanceDirection == MaintenanceBalanceDirection.DEBIT) {
            return MaintenancePremiumSettlementStatus.POSTED;
        }
        if ("SUCCEEDED".equals(currentRefundStatus)) {
            return MaintenancePremiumSettlementStatus.SETTLED;
        }
        if ("FAILED".equals(currentRefundStatus) || "CANCELLED".equals(currentRefundStatus)) {
            return MaintenancePremiumSettlementStatus.SETTLEMENT_FAILED;
        }
        return MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING;
    }

    private boolean sameFinancialSettlement(RecordMaintenanceFinancialSettlementCommand command,
                                            MaintenancePremiumSettlementStatus targetStatus) {
        return Objects.equals(refundInstructionId, command.refundInstructionId())
                && Objects.equals(refundOrderId, command.refundOrderId())
                && Objects.equals(refundStatus, command.refundStatus())
                && Objects.equals(commissionAdjustmentCount, command.commissionAdjustmentCount())
                && premiumSettlementStatus == targetStatus;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasRecordedBalanceFact() {
        return switch (premiumSettlementStatus) {
            case POSTED, SETTLEMENT_PENDING, SETTLEMENT_FAILED, SETTLED, NOT_REQUIRED -> true;
            default -> false;
        };
    }

    private boolean requiresPremiumSettlement() {
        return switch (maintenanceType) {
            case ADDITIONAL_PAYMENT, REDUCTION_PAYMENT, POLICY_TERMINATION, POLICY_PERIOD_CHANGE,
                    COVERAGE_AMOUNT_CHANGE, COVERAGE_CHANGE, TOP_UP, PARTIAL_WITHDRAWAL, REDUCED_PAID_UP ->
                true;
            default -> false;
        };
    }
}
