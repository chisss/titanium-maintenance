package com.titanium.maintenance.web.response.casecreation;

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
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

/** 独立保全案件详情响应。 */
public record MaintenanceCaseDetailVO(
        String caseId,
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
        EffectCompensationVO effectCompensation,
        EffectScheduleVO effectSchedule,
        RetroactiveImpactAnalysisVO retroactiveImpactAnalysis,
        RetroactivePeriodRecalculationVO retroactivePeriodRecalculation,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        String description,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt,
        List<ItemVO> items,
        List<WorkflowTaskVO> workflowTasks,
        List<FieldChangeVO> fieldChanges,
        SnapshotSetVO snapshots) {

    public MaintenanceCaseDetailVO {
        items = List.copyOf(items);
        workflowTasks = List.copyOf(workflowTasks);
        fieldChanges = List.copyOf(fieldChanges);
    }

    public record EffectCompensationVO(
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

    public record EffectScheduleVO(
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

    public record RetroactiveImpactAnalysisVO(
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
            List<RetroactiveImpactItemVO> items) {

        public RetroactiveImpactAnalysisVO {
            coveredDomains = List.copyOf(coveredDomains);
            items = List.copyOf(items);
        }
    }

    public record RetroactiveImpactItemVO(
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

    public record RetroactivePeriodRecalculationVO(
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
            RetroactivePeriodResolutionVO resolution,
            List<RetroactivePeriodAdjustmentVO> periods) {

        public RetroactivePeriodRecalculationVO {
            periods = List.copyOf(periods);
        }
    }

    public record RetroactivePeriodResolutionVO(
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

    public record RetroactivePeriodAdjustmentVO(
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

    public record WorkflowTaskVO(
            String taskId,
            String itemCode,
            int itemOrder,
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode,
            MaintenanceWorkflowTaskStatus status,
            WorkflowAssignmentVO assignment,
            int retryCount,
            WorkflowFailureVO failure,
            WorkflowConditionEvidenceVO conditionEvidence,
            WorkflowReviewEvidenceVO reviewEvidence,
            WorkflowUnderwritingEvidenceVO underwritingEvidence,
            WorkflowPremiumQuoteEvidenceVO premiumQuoteEvidence,
            WorkflowBillingPostingEvidenceVO billingPostingEvidence,
            WorkflowFundSettlementEvidenceVO fundSettlementEvidence,
            WorkflowEffectEvidenceVO effectEvidence,
            WorkflowOperationVO lastOperation) {
    }

    public record WorkflowAssignmentVO(String assignee, LocalDateTime claimedAt) {
    }

    public record WorkflowFailureVO(String failureCode, String failureReason) {
    }

    public record WorkflowConditionEvidenceVO(
            String ruleVersion,
            String inputHash,
            MaintenanceWorkflowConditionDecision decision,
            String reason,
            LocalDateTime decidedAt,
            String decidedBy) {
    }

    public record WorkflowReviewEvidenceVO(
            MaintenanceReviewMode mode,
            MaintenanceReviewDecision decision,
            String policyCode,
            String policyVersion,
            String contextHash,
            List<WorkflowReviewGateVO> gates,
            String comment,
            LocalDateTime decidedAt,
            String decidedBy) {

        public WorkflowReviewEvidenceVO {
            gates = List.copyOf(gates);
        }
    }

    public record WorkflowReviewGateVO(
            MaintenanceReviewGate gate,
            boolean passed,
            String evidenceHash,
            String detailCode) {
    }

    public record WorkflowUnderwritingEvidenceVO(
            String underwritingCaseId,
            String requestPayloadHash,
            String ruleVersion,
            String modelVersion,
            MaintenanceUnderwritingConclusion conclusion,
            List<String> additionalConditions,
            String summary,
            LocalDateTime completedAt) {

        public WorkflowUnderwritingEvidenceVO {
            additionalConditions = List.copyOf(additionalConditions);
        }
    }

    public record WorkflowPremiumQuoteEvidenceVO(
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

    public record WorkflowBillingPostingEvidenceVO(
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

    public record WorkflowFundSettlementEvidenceVO(
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

    public record WorkflowEffectEvidenceVO(
            WorkflowEffectRequestEvidenceVO request,
            WorkflowPolicyApplicationEvidenceVO application) {
    }

    public record WorkflowEffectRequestEvidenceVO(
            String requestId,
            String requestPayloadHash,
            long expectedPolicyVersion,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt,
            String proposedSnapshotHash,
            LocalDateTime requestedAt) {
    }

    public record WorkflowPolicyApplicationEvidenceVO(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            SnapshotReferenceVO appliedSnapshot,
            List<WorkflowAppliedFieldEvidenceVO> appliedFields,
            LocalDateTime appliedAt) {

        public WorkflowPolicyApplicationEvidenceVO {
            appliedFields = List.copyOf(appliedFields);
        }
    }

    public record WorkflowAppliedFieldEvidenceVO(
            String itemCode,
            String objectId,
            String fieldCode,
            PolicyFieldDataType dataType,
            String canonicalValue) {
    }

    public record WorkflowOperationVO(
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

    public record ItemVO(
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
    }

    public record FieldChangeVO(
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
    }

    public record SnapshotSetVO(
            SnapshotReferenceVO before,
            SnapshotReferenceVO proposed,
            SnapshotReferenceVO applied) {
    }

    public record SnapshotReferenceVO(
            String storageKey,
            String contentHash,
            Long policyVersion,
            String capturedAt) {
    }
}
