package com.titanium.maintenance.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

/** 独立保全案件详情，字段值仍是 Query 内部原始值，由 Application 决定脱敏。 */
public record MaintenanceCaseDetailQueryResult(
        String maintenanceId,
        String policyId,
        String policyNumber,
        String customerId,
        String productId,
        String productVersion,
        String planVersion,
        Long policyBaselineVersion,
        String businessEffectiveAt,
        MaintenanceChannel source,
        MaintenanceStatus status,
        MaintenanceEffectStatus effectStatus,
        EffectCompensationQueryResult effectCompensation,
        EffectScheduleQueryResult effectSchedule,
        RetroactiveImpactAnalysisQueryResult retroactiveImpactAnalysis,
        RetroactivePeriodRecalculationQueryResult retroactivePeriodRecalculation,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        String description,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt,
        List<ItemQueryResult> items,
        List<WorkflowTaskQueryResult> workflowTasks,
        List<FieldChangeQueryResult> fieldChanges,
        SnapshotSetQueryResult snapshots) {

    public MaintenanceCaseDetailQueryResult {
        items = List.copyOf(items);
        workflowTasks = List.copyOf(workflowTasks);
        fieldChanges = List.copyOf(fieldChanges);
    }

    /** 兼容 M5-04 之前不含未来生效计划的内部查询构造。 */
    public MaintenanceCaseDetailQueryResult(
            String maintenanceId,
            String policyId,
            String policyNumber,
            String customerId,
            String productId,
            String productVersion,
            String planVersion,
            Long policyBaselineVersion,
            String businessEffectiveAt,
            MaintenanceChannel source,
            MaintenanceStatus status,
            MaintenanceEffectStatus effectStatus,
            EffectCompensationQueryResult effectCompensation,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            String createdBy,
            LocalDateTime createdAt,
            String updatedBy,
            LocalDateTime updatedAt,
            List<ItemQueryResult> items,
            List<WorkflowTaskQueryResult> workflowTasks,
            List<FieldChangeQueryResult> fieldChanges,
            SnapshotSetQueryResult snapshots) {
        this(maintenanceId, policyId, policyNumber, customerId, productId, productVersion,
                planVersion, policyBaselineVersion, businessEffectiveAt, source, status, effectStatus,
                effectCompensation, null, null, null, effectiveTimeType, specificEffectiveDate, description,
                createdBy, createdAt, updatedBy, updatedAt, items, workflowTasks, fieldChanges, snapshots);
    }

    /** 兼容 M5-03 补偿事实之前的内部查询构造。 */
    public MaintenanceCaseDetailQueryResult(
            String maintenanceId,
            String policyId,
            String policyNumber,
            String customerId,
            String productId,
            String productVersion,
            String planVersion,
            Long policyBaselineVersion,
            String businessEffectiveAt,
            MaintenanceChannel source,
            MaintenanceStatus status,
            MaintenanceEffectStatus effectStatus,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            String createdBy,
            LocalDateTime createdAt,
            String updatedBy,
            LocalDateTime updatedAt,
            List<ItemQueryResult> items,
            List<WorkflowTaskQueryResult> workflowTasks,
            List<FieldChangeQueryResult> fieldChanges,
            SnapshotSetQueryResult snapshots) {
        this(maintenanceId, policyId, policyNumber, customerId, productId, productVersion,
                planVersion, policyBaselineVersion, businessEffectiveAt, source, status, effectStatus,
                null, null, null, null, effectiveTimeType, specificEffectiveDate, description, createdBy, createdAt,
                updatedBy, updatedAt, items, workflowTasks, fieldChanges, snapshots);
    }

    /** 兼容 M5-01 之前不含正交生效状态的内部查询构造。 */
    public MaintenanceCaseDetailQueryResult(
            String maintenanceId,
            String policyId,
            String policyNumber,
            String customerId,
            String productId,
            String productVersion,
            String planVersion,
            Long policyBaselineVersion,
            String businessEffectiveAt,
            MaintenanceChannel source,
            MaintenanceStatus status,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            String createdBy,
            LocalDateTime createdAt,
            String updatedBy,
            LocalDateTime updatedAt,
            List<ItemQueryResult> items,
            List<WorkflowTaskQueryResult> workflowTasks,
            List<FieldChangeQueryResult> fieldChanges,
            SnapshotSetQueryResult snapshots) {
        this(maintenanceId, policyId, policyNumber, customerId, productId, productVersion,
                planVersion, policyBaselineVersion, businessEffectiveAt, source, status,
                MaintenanceEffectStatus.NOT_STARTED, null, null, null, null, effectiveTimeType, specificEffectiveDate,
                description, createdBy, createdAt, updatedBy, updatedAt, items, workflowTasks,
                fieldChanges, snapshots);
    }

    public record EffectCompensationQueryResult(
            boolean required,
            String compensationId,
            String requestId,
            String endorsementNo,
            Long actualPolicyVersion,
            String applicationHash,
            String failureReason,
            LocalDateTime recordedAt,
            LocalDateTime resolvedAt,
            String resolvedBy) {
    }

    public record EffectScheduleQueryResult(
            String scheduleId,
            MaintenanceEffectScheduleStatus status,
            String tenantZoneId,
            LocalDateTime nextExecutionAt,
            int attemptCount,
            String lastAttemptId,
            LocalDateTime lastAttemptAt,
            String lastErrorCode,
            String lastErrorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record RetroactiveImpactAnalysisQueryResult(
            String analysisId,
            int analysisVersion,
            String operationId,
            String requestHash,
            LocalDateTime scopeFrom,
            LocalDateTime scopeTo,
            MaintenanceRetroactiveImpactAnalysisStatus status,
            List<MaintenanceRetroactiveImpactDomain> coveredDomains,
            int itemCount,
            int blockingItemCount,
            int pendingItemCount,
            String evidenceVersion,
            String resultHash,
            String failureCode,
            String failureMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt,
            List<RetroactiveImpactItemQueryResult> items) {

        public RetroactiveImpactAnalysisQueryResult {
            coveredDomains = List.copyOf(coveredDomains);
            items = List.copyOf(items);
        }
    }

    public record RetroactiveImpactItemQueryResult(
            String itemId,
            MaintenanceRetroactiveImpactDomain sourceDomain,
            MaintenanceRetroactiveImpactType impactType,
            String referenceId,
            String referenceNumber,
            LocalDateTime occurredAt,
            String sourceStatus,
            BigDecimal amount,
            String currency,
            MaintenanceRetroactiveImpactSeverity severity,
            MaintenanceRetroactiveImpactItemStatus handlingStatus,
            String summary,
            String evidenceVersion,
            String evidenceHash) {
    }

    public record RetroactivePeriodRecalculationQueryResult(
            String periodRecalculationId,
            int periodRecalculationVersion,
            String operationId,
            String requestHash,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            MaintenanceRetroactivePeriodRecalculationStatus status,
            String productRecalculationId,
            String productRecalculationVersion,
            String originalCalculationId,
            String originalResultHash,
            String replacementCalculationId,
            String replacementResultHash,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            String productInputHash,
            String productResultHash,
            LocalDateTime productCalculatedAt,
            int periodCount,
            String billingBatchId,
            String billingStatus,
            int billingPostedCount,
            int billingReviewCount,
            String billingRequestHash,
            String billingResultHash,
            LocalDateTime billingAdjustedAt,
            String failureCode,
            String failureMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt,
            RetroactivePeriodResolutionQueryResult resolution,
            List<RetroactivePeriodAdjustmentQueryResult> periods) {

        public RetroactivePeriodRecalculationQueryResult {
            periods = List.copyOf(periods);
        }
    }

    public record RetroactivePeriodResolutionQueryResult(
            String periodResolutionId,
            String operationId,
            String requestHash,
            MaintenanceRetroactivePeriodResolutionStatus status,
            String billingResolutionId,
            String sourceBatchResultHash,
            String targetAccountingPeriod,
            int resolvedLineCount,
            String resultHash,
            String reason,
            String failureCode,
            String failureMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt) {
    }

    public record RetroactivePeriodAdjustmentQueryResult(
            String periodId,
            String sourceReferenceId,
            String accountingPeriod,
            LocalDateTime periodStart,
            BigDecimal originalAmount,
            BigDecimal recalculatedAmount,
            MaintenanceBalanceDirection direction,
            BigDecimal differenceAmount,
            String currency,
            String billingStatus,
            String sourceEvidenceHash,
            String productResultHash,
            String billingResultHash,
            String targetAccountingPeriod,
            String resolutionStatus,
            String postingReference,
            String resolutionResultHash) {
    }

    /** 单个冻结步骤对应的案件流程任务。 */
    public record WorkflowTaskQueryResult(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            WorkflowAssignmentQueryResult assignment,
            int retryCount,
            WorkflowFailureQueryResult failure,
            WorkflowConditionEvidenceQueryResult conditionEvidence,
            WorkflowReviewEvidenceQueryResult reviewEvidence,
            WorkflowUnderwritingEvidenceQueryResult underwritingEvidence,
            WorkflowPremiumQuoteEvidenceQueryResult premiumQuoteEvidence,
            WorkflowBillingPostingEvidenceQueryResult billingPostingEvidence,
            WorkflowFundSettlementEvidenceQueryResult fundSettlementEvidence,
            WorkflowEffectEvidenceQueryResult effectEvidence,
            WorkflowOperationQueryResult lastOperation) {

        /** 兼容 M4-04 之前不含核保证据的内部查询构造。 */
        public WorkflowTaskQueryResult(
                String taskId,
                String itemCode,
                int itemOrder,
                int sequence,
                MaintenanceStepType stepType,
                MaintenanceStepMode mode,
                String conditionRuleCode,
                MaintenanceWorkflowTaskStatus status,
                WorkflowAssignmentQueryResult assignment,
                int retryCount,
                WorkflowFailureQueryResult failure,
                WorkflowConditionEvidenceQueryResult conditionEvidence,
                WorkflowReviewEvidenceQueryResult reviewEvidence,
                WorkflowOperationQueryResult lastOperation) {
            this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence,
                    reviewEvidence, null, null, null, null, null, lastOperation);
        }

        /** 兼容 M4-05 之前含核保证据、但不含报价证据的内部查询构造。 */
        public WorkflowTaskQueryResult(
                String taskId,
                String itemCode,
                int itemOrder,
                int sequence,
                MaintenanceStepType stepType,
                MaintenanceStepMode mode,
                String conditionRuleCode,
                MaintenanceWorkflowTaskStatus status,
                WorkflowAssignmentQueryResult assignment,
                int retryCount,
                WorkflowFailureQueryResult failure,
                WorkflowConditionEvidenceQueryResult conditionEvidence,
                WorkflowReviewEvidenceQueryResult reviewEvidence,
                WorkflowUnderwritingEvidenceQueryResult underwritingEvidence,
                WorkflowOperationQueryResult lastOperation) {
            this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence,
                    reviewEvidence, underwritingEvidence, null, null, null, null, lastOperation);
        }

        /** 兼容 M4-06 之前含报价、但不含入账和资金证据的内部查询构造。 */
        public WorkflowTaskQueryResult(
                String taskId,
                String itemCode,
                int itemOrder,
                int sequence,
                MaintenanceStepType stepType,
                MaintenanceStepMode mode,
                String conditionRuleCode,
                MaintenanceWorkflowTaskStatus status,
                WorkflowAssignmentQueryResult assignment,
                int retryCount,
                WorkflowFailureQueryResult failure,
                WorkflowConditionEvidenceQueryResult conditionEvidence,
                WorkflowReviewEvidenceQueryResult reviewEvidence,
                WorkflowUnderwritingEvidenceQueryResult underwritingEvidence,
                WorkflowPremiumQuoteEvidenceQueryResult premiumQuoteEvidence,
                WorkflowOperationQueryResult lastOperation) {
            this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                    underwritingEvidence, premiumQuoteEvidence, null, null, null, lastOperation);
        }

        /** 兼容 M5-01 之前含资金证据、但不含生效证据的内部查询构造。 */
        public WorkflowTaskQueryResult(
                String taskId,
                String itemCode,
                int itemOrder,
                int sequence,
                MaintenanceStepType stepType,
                MaintenanceStepMode mode,
                String conditionRuleCode,
                MaintenanceWorkflowTaskStatus status,
                WorkflowAssignmentQueryResult assignment,
                int retryCount,
                WorkflowFailureQueryResult failure,
                WorkflowConditionEvidenceQueryResult conditionEvidence,
                WorkflowReviewEvidenceQueryResult reviewEvidence,
                WorkflowUnderwritingEvidenceQueryResult underwritingEvidence,
                WorkflowPremiumQuoteEvidenceQueryResult premiumQuoteEvidence,
                WorkflowBillingPostingEvidenceQueryResult billingPostingEvidence,
                WorkflowFundSettlementEvidenceQueryResult fundSettlementEvidence,
                WorkflowOperationQueryResult lastOperation) {
            this(taskId, itemCode, itemOrder, sequence, stepType, mode, conditionRuleCode,
                    status, assignment, retryCount, failure, conditionEvidence, reviewEvidence,
                    underwritingEvidence, premiumQuoteEvidence, billingPostingEvidence,
                    fundSettlementEvidence, null, lastOperation);
        }
    }

    public record WorkflowAssignmentQueryResult(
            String assignee,
            LocalDateTime claimedAt) {
    }

    public record WorkflowFailureQueryResult(
            String failureCode,
            String failureReason) {
    }

    public record WorkflowConditionEvidenceQueryResult(
            String ruleVersion,
            String inputHash,
            MaintenanceWorkflowConditionDecision decision,
            String reason,
            LocalDateTime decidedAt,
            String decidedBy) {
    }

    public record WorkflowReviewEvidenceQueryResult(
            MaintenanceReviewMode mode,
            MaintenanceReviewDecision decision,
            String policyCode,
            String policyVersion,
            String contextHash,
            List<WorkflowReviewGateQueryResult> gates,
            String comment,
            LocalDateTime decidedAt,
            String decidedBy) {

        public WorkflowReviewEvidenceQueryResult {
            gates = List.copyOf(gates);
        }
    }

    public record WorkflowReviewGateQueryResult(
            MaintenanceReviewGate gate,
            boolean passed,
            String evidenceHash,
            String detailCode) {
    }

    public record WorkflowUnderwritingEvidenceQueryResult(
            String underwritingCaseId,
            String requestPayloadHash,
            String ruleVersion,
            String modelVersion,
            MaintenanceUnderwritingConclusion conclusion,
            List<String> additionalConditions,
            String summary,
            LocalDateTime completedAt) {

        public WorkflowUnderwritingEvidenceQueryResult {
            additionalConditions = List.copyOf(additionalConditions);
        }
    }

    public record WorkflowPremiumQuoteEvidenceQueryResult(
            MaintenancePremiumQuoteStatus status,
            String quoteId,
            String quoteVersion,
            String requestPayloadHash,
            String originalCalculationId,
            String originalResultHash,
            String replacementCalculationId,
            String replacementResultHash,
            String pricingPlanVersion,
            String pricingPlanContentHash,
            String resultHash,
            String detailSummary,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            LocalDateTime quotedAt,
            LocalDateTime validUntil) {
    }

    public record WorkflowBillingPostingEvidenceQueryResult(
            String postingId,
            String adjustmentId,
            String resultHash,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            MaintenanceBillingPostingStatus status,
            Integer commissionAdjustmentCount,
            LocalDateTime recordedAt) {
    }

    public record WorkflowFundSettlementEvidenceQueryResult(
            MaintenanceFundSettlementType type,
            MaintenanceFundSettlementStatus status,
            String sourcePostingId,
            String instructionId,
            String orderId,
            String externalStatus,
            BigDecimal amount,
            String currency,
            String failureCode,
            String failureMessage,
            LocalDateTime recordedAt) {
    }

    public record WorkflowEffectEvidenceQueryResult(
            WorkflowEffectRequestEvidenceQueryResult request,
            WorkflowPolicyApplicationEvidenceQueryResult application) {
    }

    public record WorkflowEffectRequestEvidenceQueryResult(
            String requestId,
            String requestPayloadHash,
            long expectedPolicyVersion,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt,
            String proposedSnapshotHash,
            LocalDateTime requestedAt) {
    }

    public record WorkflowPolicyApplicationEvidenceQueryResult(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            SnapshotReferenceQueryResult appliedSnapshot,
            List<WorkflowAppliedFieldEvidenceQueryResult> appliedFields,
            LocalDateTime appliedAt,
            PolicyMaintenanceAction stateAction,
            String statusBefore,
            String statusAfter) {

        public WorkflowPolicyApplicationEvidenceQueryResult {
            appliedFields = List.copyOf(appliedFields);
            stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
        }

        /** 兼容 M5-02 字段型查询结果构造。 */
        public WorkflowPolicyApplicationEvidenceQueryResult(
                String requestId,
                String endorsementNo,
                long expectedPolicyVersion,
                long actualPolicyVersion,
                String applicationHash,
                SnapshotReferenceQueryResult appliedSnapshot,
                List<WorkflowAppliedFieldEvidenceQueryResult> appliedFields,
                LocalDateTime appliedAt) {
            this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion,
                    applicationHash, appliedSnapshot, appliedFields, appliedAt,
                    PolicyMaintenanceAction.NONE, null, null);
        }
    }

    public record WorkflowAppliedFieldEvidenceQueryResult(
            String itemCode,
            String objectId,
            String fieldCode,
            PolicyFieldDataType dataType,
            String canonicalValue) {
    }

    public record WorkflowOperationQueryResult(
            String operationId,
            MaintenanceWorkflowAction action,
            String payloadHash,
            String evidenceVersion,
            String evidenceHash,
            String resultCode,
            String reason,
            LocalDateTime operatedAt,
            String operatedBy) {
    }

    /** 案件冻结的单个保全项配置和 Product Offering 证据。 */
    public record ItemQueryResult(
            String itemCode,
            String itemName,
            String itemCategory,
            String configurationId,
            String configurationVersion,
            String configurationContentHash,
            String offeringId,
            String offeringVersion,
            String offeringContentHash,
            String evidenceResolvedAt,
            LocalDateTime selectedAt,
            MaintenanceItemWithdrawalStatus withdrawalStatus,
            String withdrawalOperationId,
            String withdrawalRequestHash,
            String withdrawalReason,
            String withdrawalSourcePostingId,
            String withdrawalSourceResultHash,
            MaintenanceBalanceDirection withdrawalSourceDirection,
            MaintenanceFundSettlementStatus withdrawalSourceFundStatus,
            String withdrawalReversalId,
            String withdrawalReversalResultHash,
            MaintenanceBalanceDirection withdrawalReversalDirection,
            BigDecimal withdrawalAmount,
            String withdrawalCurrency,
            MaintenanceItemWithdrawalFundAction withdrawalFundAction,
            MaintenanceFundSettlementStatus withdrawalFundStatus,
            String withdrawalFundRequestId,
            String withdrawalFundOrderId,
            String withdrawalFundExternalStatus,
            String withdrawalFailureCode,
            String withdrawalFailureMessage,
            Integer withdrawalRetryCount,
            LocalDateTime withdrawalRequestedAt,
            LocalDateTime withdrawalCompletedAt,
            String withdrawalRequestedBy,
            String withdrawalUpdatedBy) {

        /** 兼容 M5-06B 之前不含项目撤销信息的内部查询构造。 */
        public ItemQueryResult(
                String itemCode,
                String itemName,
                String itemCategory,
                String configurationId,
                String configurationVersion,
                String configurationContentHash,
                String offeringId,
                String offeringVersion,
                String offeringContentHash,
                String evidenceResolvedAt,
                LocalDateTime selectedAt) {
            this(itemCode, itemName, itemCategory, configurationId, configurationVersion,
                    configurationContentHash, offeringId, offeringVersion, offeringContentHash,
                    evidenceResolvedAt, selectedAt, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null);
        }
    }

    /** 单个业务对象字段的四值差异。 */
    public record FieldChangeQueryResult(
            String itemCode,
            String objectId,
            String fieldCode,
            String labelKey,
            PolicyFieldDataType dataType,
            String baseValue,
            String currentValue,
            String proposedValue,
            String appliedValue,
            MaintenanceFieldConflictStatus conflictStatus,
            String resolutionCode,
            String conflictOperationId,
            LocalDateTime conflictDetectedAt,
            Long conflictPolicyVersion,
            String conflictEvidenceHash,
            String resolutionOperationId,
            String resolutionReason,
            String resolutionEvidenceHash,
            String resolvedBy,
            LocalDateTime resolvedAt,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            String changeTypeCode) {

        /** 兼容 M5-06A 之前不含冲突审计字段的内部查询构造。 */
        public FieldChangeQueryResult(
                String itemCode,
                String objectId,
                String fieldCode,
                String labelKey,
                PolicyFieldDataType dataType,
                String baseValue,
                String currentValue,
                String proposedValue,
                String appliedValue,
                MaintenanceFieldConflictStatus conflictStatus,
                String resolutionCode,
                PolicyFieldSensitivityLevel sensitivity,
                PolicyFieldMaskingPolicy maskingPolicy,
                String changeTypeCode) {
            this(itemCode, objectId, fieldCode, labelKey, dataType, baseValue, currentValue,
                    proposedValue, appliedValue, conflictStatus, resolutionCode, null, null, null,
                    null, null, null, null, null, null, sensitivity, maskingPolicy, changeTypeCode);
        }
    }

    /** 三类大快照引用。 */
    public record SnapshotSetQueryResult(
            SnapshotReferenceQueryResult before,
            SnapshotReferenceQueryResult proposed,
            SnapshotReferenceQueryResult applied) {
    }

    /** 单个不可变快照引用。 */
    public record SnapshotReferenceQueryResult(
            String storageKey,
            String contentHash,
            Long policyVersion,
            String capturedAt) {
    }
}
