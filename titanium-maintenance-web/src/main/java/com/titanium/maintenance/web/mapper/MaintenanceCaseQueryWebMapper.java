package com.titanium.maintenance.web.mapper;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectCompensationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectScheduleQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactiveImpactAnalysisQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodRecalculationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotReferenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.EffectCompensationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.EffectScheduleVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.FieldChangeVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.ItemVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactiveImpactAnalysisVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactiveImpactItemVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodAdjustmentVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodRecalculationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.RetroactivePeriodResolutionVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.SnapshotReferenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.SnapshotSetVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowAppliedFieldEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowAssignmentVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowBillingPostingEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowConditionEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowEffectEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowEffectRequestEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowFailureVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowFundSettlementEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowOperationVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowPolicyApplicationEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowPremiumQuoteEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowReviewEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowReviewGateVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowTaskVO;
import com.titanium.maintenance.web.response.MaintenanceCaseDetailVO.WorkflowUnderwritingEvidenceVO;
import com.titanium.maintenance.web.response.MaintenanceCasePageVO;
import com.titanium.maintenance.web.response.MaintenanceCasePageVO.MaintenanceCaseSummaryVO;

/** 独立保全案件 Query 结果到 Web VO 的协议映射。 */
@Component
public class MaintenanceCaseQueryWebMapper {

    public MaintenanceCasePageVO toPageVO(MaintenanceCasePageQueryResult result) {
        return new MaintenanceCasePageVO(
                result.list().stream()
                        .map(item -> new MaintenanceCaseSummaryVO(
                                item.maintenanceId(), item.policyId(), item.policyNumber(), item.customerId(),
                                item.itemCodes(), item.source(), item.status(), item.effectStatus(), item.operatorId(),
                                item.createdAt(), item.updatedAt()))
                        .toList(),
                result.total(), result.page(), result.size(), result.totalPages());
    }

    public MaintenanceCaseDetailVO toDetailVO(MaintenanceCaseDetailQueryResult result) {
        return new MaintenanceCaseDetailVO(
                result.maintenanceId(), result.policyId(), result.policyNumber(), result.customerId(),
                result.productId(), result.productVersion(), result.planVersion(), result.policyBaselineVersion(),
                result.businessEffectiveAt(), result.source(), result.status(), result.effectStatus(),
                effectCompensation(result.effectCompensation()), effectSchedule(result.effectSchedule()),
                retroactiveImpactAnalysis(result.retroactiveImpactAnalysis()),
                retroactivePeriodRecalculation(result.retroactivePeriodRecalculation()), result.effectiveTimeType(),
                result.specificEffectiveDate(), result.description(), result.createdBy(), result.createdAt(),
                result.updatedBy(), result.updatedAt(),
                result.items().stream()
                        .map(item -> new ItemVO(
                                item.itemCode(), item.itemName(), item.itemCategory(), item.configurationId(),
                                item.configurationVersion(), item.configurationContentHash(), item.offeringId(),
                                item.offeringVersion(), item.offeringContentHash(), item.evidenceResolvedAt(),
                                item.selectedAt(), item.withdrawalStatus(), item.withdrawalOperationId(),
                                item.withdrawalRequestHash(), item.withdrawalReason(),
                                item.withdrawalSourcePostingId(), item.withdrawalSourceResultHash(),
                                item.withdrawalSourceDirection(), item.withdrawalSourceFundStatus(),
                                item.withdrawalReversalId(),
                                item.withdrawalReversalResultHash(), item.withdrawalReversalDirection(),
                                item.withdrawalAmount(), item.withdrawalCurrency(), item.withdrawalFundAction(),
                                item.withdrawalFundStatus(), item.withdrawalFundRequestId(),
                                item.withdrawalFundOrderId(), item.withdrawalFundExternalStatus(),
                                item.withdrawalFailureCode(), item.withdrawalFailureMessage(),
                                item.withdrawalRetryCount(), item.withdrawalRequestedAt(),
                                item.withdrawalCompletedAt(), item.withdrawalRequestedBy(),
                                item.withdrawalUpdatedBy()))
                        .toList(),
                result.workflowTasks().stream()
                        .map(task -> new WorkflowTaskVO(
                                task.taskId(), task.itemCode(), task.itemOrder(), task.sequence(),
                                task.stepType(), task.mode(), task.conditionRuleCode(), task.status(),
                                task.assignment() == null
                                        ? null
                                        : new WorkflowAssignmentVO(
                                                task.assignment().assignee(), task.assignment().claimedAt()),
                                task.retryCount(),
                                task.failure() == null
                                        ? null
                                        : new WorkflowFailureVO(
                                                task.failure().failureCode(), task.failure().failureReason()),
                                task.conditionEvidence() == null
                                        ? null
                                        : new WorkflowConditionEvidenceVO(
                                                task.conditionEvidence().ruleVersion(),
                                                task.conditionEvidence().inputHash(),
                                                task.conditionEvidence().decision(),
                                                task.conditionEvidence().reason(),
                                                task.conditionEvidence().decidedAt(),
                                                task.conditionEvidence().decidedBy()),
                                task.reviewEvidence() == null
                                        ? null
                                        : new WorkflowReviewEvidenceVO(
                                                task.reviewEvidence().mode(),
                                                task.reviewEvidence().decision(),
                                                task.reviewEvidence().policyCode(),
                                                task.reviewEvidence().policyVersion(),
                                                task.reviewEvidence().contextHash(),
                                                task.reviewEvidence().gates().stream()
                                                        .map(gate -> new WorkflowReviewGateVO(
                                                                gate.gate(), gate.passed(),
                                                                gate.evidenceHash(), gate.detailCode()))
                                                        .toList(),
                                                task.reviewEvidence().comment(),
                                                task.reviewEvidence().decidedAt(),
                                                task.reviewEvidence().decidedBy()),
                                task.underwritingEvidence() == null
                                        ? null
                                        : new WorkflowUnderwritingEvidenceVO(
                                                task.underwritingEvidence().underwritingCaseId(),
                                                task.underwritingEvidence().requestPayloadHash(),
                                                task.underwritingEvidence().ruleVersion(),
                                                task.underwritingEvidence().modelVersion(),
                                                task.underwritingEvidence().conclusion(),
                                                task.underwritingEvidence().additionalConditions(),
                                                task.underwritingEvidence().summary(),
                                                task.underwritingEvidence().completedAt()),
                                task.premiumQuoteEvidence() == null
                                        ? null
                                        : new WorkflowPremiumQuoteEvidenceVO(
                                                task.premiumQuoteEvidence().status(),
                                                task.premiumQuoteEvidence().quoteId(),
                                                task.premiumQuoteEvidence().quoteVersion(),
                                                task.premiumQuoteEvidence().requestPayloadHash(),
                                                task.premiumQuoteEvidence().originalCalculationId(),
                                                task.premiumQuoteEvidence().originalResultHash(),
                                                task.premiumQuoteEvidence().replacementCalculationId(),
                                                task.premiumQuoteEvidence().replacementResultHash(),
                                                task.premiumQuoteEvidence().pricingPlanVersion(),
                                                task.premiumQuoteEvidence().pricingPlanContentHash(),
                                                task.premiumQuoteEvidence().resultHash(),
                                                task.premiumQuoteEvidence().detailSummary(),
                                                task.premiumQuoteEvidence().direction(),
                                                task.premiumQuoteEvidence().amount(),
                                                task.premiumQuoteEvidence().currency(),
                                                task.premiumQuoteEvidence().quotedAt(),
                                                task.premiumQuoteEvidence().validUntil()),
                                task.billingPostingEvidence() == null
                                        ? null
                                        : new WorkflowBillingPostingEvidenceVO(
                                                task.billingPostingEvidence().postingId(),
                                                task.billingPostingEvidence().adjustmentId(),
                                                task.billingPostingEvidence().resultHash(),
                                                task.billingPostingEvidence().direction(),
                                                task.billingPostingEvidence().amount(),
                                                task.billingPostingEvidence().currency(),
                                                task.billingPostingEvidence().status(),
                                                task.billingPostingEvidence().commissionAdjustmentCount(),
                                                task.billingPostingEvidence().recordedAt()),
                                task.fundSettlementEvidence() == null
                                        ? null
                                        : new WorkflowFundSettlementEvidenceVO(
                                                task.fundSettlementEvidence().type(),
                                                task.fundSettlementEvidence().status(),
                                                task.fundSettlementEvidence().sourcePostingId(),
                                                task.fundSettlementEvidence().instructionId(),
                                                task.fundSettlementEvidence().orderId(),
                                                task.fundSettlementEvidence().externalStatus(),
                                                task.fundSettlementEvidence().amount(),
                                                task.fundSettlementEvidence().currency(),
                                                task.fundSettlementEvidence().failureCode(),
                                                task.fundSettlementEvidence().failureMessage(),
                                                task.fundSettlementEvidence().recordedAt()),
                                effectEvidence(task.effectEvidence()),
                                task.lastOperation() == null
                                        ? null
                                        : new WorkflowOperationVO(
                                                task.lastOperation().operationId(),
                                                task.lastOperation().action(),
                                                task.lastOperation().payloadHash(),
                                                task.lastOperation().evidenceVersion(),
                                                task.lastOperation().evidenceHash(),
                                                task.lastOperation().resultCode(),
                                                task.lastOperation().reason(),
                                                task.lastOperation().operatedAt(),
                                                task.lastOperation().operatedBy())))
                        .toList(),
                result.fieldChanges().stream()
                        .map(field -> new FieldChangeVO(
                                field.itemCode(), field.objectId(), field.fieldCode(), field.labelKey(),
                                field.dataType(), field.baseValue(), field.currentValue(), field.proposedValue(),
                                field.appliedValue(), field.conflictStatus(), field.resolutionCode(),
                                field.conflictOperationId(), field.conflictDetectedAt(), field.conflictPolicyVersion(),
                                field.conflictEvidenceHash(), field.resolutionOperationId(), field.resolutionReason(),
                                field.resolutionEvidenceHash(), field.resolvedBy(), field.resolvedAt(),
                                field.sensitivity(), field.maskingPolicy(), field.changeTypeCode()))
                        .toList(),
                new SnapshotSetVO(
                        reference(result.snapshots().before()),
                        reference(result.snapshots().proposed()),
                        reference(result.snapshots().applied())));
    }

    private EffectCompensationVO effectCompensation(EffectCompensationQueryResult compensation) {
        return compensation == null
                ? null
                : new EffectCompensationVO(
                        compensation.required(), compensation.compensationId(), compensation.requestId(),
                        compensation.endorsementNo(), compensation.actualPolicyVersion(), compensation.applicationHash(),
                        compensation.failureReason(), compensation.recordedAt(), compensation.resolvedAt(),
                        compensation.resolvedBy());
    }

    private EffectScheduleVO effectSchedule(EffectScheduleQueryResult schedule) {
        return schedule == null
                ? null
                : new EffectScheduleVO(
                        schedule.scheduleId(), schedule.status(), schedule.tenantZoneId(),
                        schedule.nextExecutionAt(), schedule.attemptCount(), schedule.lastAttemptId(),
                        schedule.lastAttemptAt(), schedule.lastErrorCode(), schedule.lastErrorMessage(),
                        schedule.createdAt(), schedule.updatedAt());
    }

    private RetroactiveImpactAnalysisVO retroactiveImpactAnalysis(
            RetroactiveImpactAnalysisQueryResult analysis) {
        if (analysis == null) {
            return null;
        }
        return new RetroactiveImpactAnalysisVO(
                analysis.analysisId(), analysis.analysisVersion(), analysis.operationId(), analysis.requestHash(),
                analysis.scopeFrom(), analysis.scopeTo(), analysis.status(), analysis.coveredDomains(),
                analysis.itemCount(), analysis.blockingItemCount(), analysis.pendingItemCount(),
                analysis.evidenceVersion(), analysis.resultHash(), analysis.failureCode(), analysis.failureMessage(),
                analysis.startedAt(), analysis.completedAt(), analysis.updatedAt(),
                analysis.items().stream()
                        .map(item -> new RetroactiveImpactItemVO(
                                item.itemId(), item.sourceDomain(), item.impactType(), item.referenceId(),
                                item.referenceNumber(), item.occurredAt(), item.sourceStatus(), item.amount(),
                                item.currency(), item.severity(), item.handlingStatus(), item.summary(),
                                item.evidenceVersion(), item.evidenceHash()))
                        .toList());
    }

    private RetroactivePeriodRecalculationVO retroactivePeriodRecalculation(
            RetroactivePeriodRecalculationQueryResult recalculation) {
        if (recalculation == null) {
            return null;
        }
        return new RetroactivePeriodRecalculationVO(
                recalculation.periodRecalculationId(), recalculation.periodRecalculationVersion(),
                recalculation.operationId(), recalculation.requestHash(), recalculation.analysisId(),
                recalculation.analysisVersion(), recalculation.analysisResultHash(), recalculation.status(),
                recalculation.productRecalculationId(), recalculation.productRecalculationVersion(),
                recalculation.originalCalculationId(), recalculation.originalResultHash(),
                recalculation.replacementCalculationId(), recalculation.replacementResultHash(),
                recalculation.direction(), recalculation.amount(), recalculation.currency(),
                recalculation.productInputHash(), recalculation.productResultHash(),
                recalculation.productCalculatedAt(), recalculation.periodCount(), recalculation.billingBatchId(),
                recalculation.billingStatus(), recalculation.billingPostedCount(), recalculation.billingReviewCount(),
                recalculation.billingRequestHash(), recalculation.billingResultHash(),
                recalculation.billingAdjustedAt(), recalculation.failureCode(), recalculation.failureMessage(),
                recalculation.startedAt(), recalculation.completedAt(), recalculation.updatedAt(),
                recalculation.resolution() == null
                        ? null
                        : new RetroactivePeriodResolutionVO(
                                recalculation.resolution().periodResolutionId(),
                                recalculation.resolution().operationId(), recalculation.resolution().requestHash(),
                                recalculation.resolution().status(),
                                recalculation.resolution().billingResolutionId(),
                                recalculation.resolution().sourceBatchResultHash(),
                                recalculation.resolution().targetAccountingPeriod(),
                                recalculation.resolution().resolvedLineCount(),
                                recalculation.resolution().resultHash(), recalculation.resolution().reason(),
                                recalculation.resolution().failureCode(),
                                recalculation.resolution().failureMessage(),
                                recalculation.resolution().startedAt(),
                                recalculation.resolution().completedAt(),
                                recalculation.resolution().updatedAt()),
                recalculation.periods().stream()
                        .map(period -> new RetroactivePeriodAdjustmentVO(
                                period.periodId(), period.sourceReferenceId(), period.accountingPeriod(),
                                period.periodStart(), period.originalAmount(), period.recalculatedAmount(),
                                period.direction(), period.differenceAmount(), period.currency(),
                                period.billingStatus(), period.sourceEvidenceHash(), period.productResultHash(),
                                period.billingResultHash(), period.targetAccountingPeriod(),
                                period.resolutionStatus(), period.postingReference(),
                                period.resolutionResultHash()))
                        .toList());
    }

    private WorkflowEffectEvidenceVO effectEvidence(WorkflowEffectEvidenceQueryResult evidence) {
        if (evidence == null) {
            return null;
        }
        var request = evidence.request();
        var application = evidence.application();
        return new WorkflowEffectEvidenceVO(
                new WorkflowEffectRequestEvidenceVO(
                        request.requestId(), request.requestPayloadHash(), request.expectedPolicyVersion(),
                        request.effectiveTimeType(), request.requestedEffectiveAt(),
                        request.proposedSnapshotHash(), request.requestedAt()),
                application == null
                        ? null
                        : new WorkflowPolicyApplicationEvidenceVO(
                                application.requestId(), application.endorsementNo(),
                                application.expectedPolicyVersion(), application.actualPolicyVersion(),
                                application.applicationHash(), reference(application.appliedSnapshot()),
                                application.appliedFields().stream()
                                        .map(field -> new WorkflowAppliedFieldEvidenceVO(
                                                field.itemCode(), field.objectId(), field.fieldCode(),
                                                field.dataType(), field.canonicalValue()))
                                        .toList(),
                                application.appliedAt()));
    }

    private SnapshotReferenceVO reference(SnapshotReferenceQueryResult reference) {
        return reference == null
                ? null
                : new SnapshotReferenceVO(
                        reference.storageKey(), reference.contentHash(),
                        reference.policyVersion(), reference.capturedAt());
    }
}
