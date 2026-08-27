package com.titanium.maintenance.query.service.impl;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectCompensationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectScheduleQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.FieldChangeQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.ItemQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactiveImpactAnalysisQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactiveImpactItemQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodAdjustmentQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodRecalculationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.RetroactivePeriodResolutionQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotReferenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotSetQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAppliedFieldEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAssignmentQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowBillingPostingEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowConditionEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectRequestEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowFailureQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowFundSettlementEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowOperationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPolicyApplicationEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPremiumQuoteEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowReviewEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowReviewGateQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowTaskQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowUnderwritingEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult.MaintenanceCaseSummaryQueryResult;
import com.titanium.maintenance.query.service.MaintenanceCaseQueryService;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceReviewGateEvidence;

import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/** M3-06 独立案件查询实现，所有过滤在数据库分页计数前完成。 */
@Service
@RequiredArgsConstructor
public class MaintenanceCaseQueryServiceImpl implements MaintenanceCaseQueryService {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceCaseItemViewRepository itemViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;
    private final MaintenanceWorkflowTaskViewRepository workflowTaskViewRepository;
    private final MaintenanceRetroactiveImpactItemViewRepository retroactiveImpactItemViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository retroactivePeriodAdjustmentViewRepository;

    @Override
    @Transactional(readOnly = true)
    public MaintenanceCasePageQueryResult search(
            String tenantId, MaintenanceCaseSearchCriteria criteria) {
        Page<MaintenanceView> page = maintenanceViewRepository.findAll(
                specification(tenantId, criteria),
                PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.DESC, "createTime")));
        List<String> maintenanceIds = page.getContent().stream()
                .map(MaintenanceView::getMaintenanceId)
                .toList();
        Map<String, List<String>> itemCodes = maintenanceIds.isEmpty()
                ? Map.of()
                : itemViewRepository.findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAsc(
                                tenantId, maintenanceIds).stream()
                        .collect(Collectors.groupingBy(
                                MaintenanceCaseItemView::getMaintenanceId,
                                Collectors.mapping(MaintenanceCaseItemView::getItemCode, Collectors.toList())));
        List<MaintenanceCaseSummaryQueryResult> list = page.getContent().stream()
                .map(view -> toSummary(view, itemCodes.getOrDefault(view.getMaintenanceId(), List.of())))
                .toList();
        return new MaintenanceCasePageQueryResult(
                list, page.getTotalElements(), criteria.page(), criteria.size(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceCaseDetailQueryResult> findDetail(String tenantId, String maintenanceId) {
        return maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        maintenanceId, tenantId)
                .map(view -> toDetail(
                        view,
                        itemViewRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAsc(
                                tenantId, maintenanceId),
                        fieldChangeViewRepository
                                .findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                                        tenantId, maintenanceId),
                        workflowTaskViewRepository
                                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                                        tenantId, maintenanceId),
                        view.getRetroactiveImpactAnalysisId() == null
                                ? List.of()
                                : retroactiveImpactItemViewRepository
                                        .findByTenantIdAndMaintenanceIdAndAnalysisId(
                                                tenantId, maintenanceId, view.getRetroactiveImpactAnalysisId()),
                        view.getRetroactivePeriodRecalculationId() == null
                                ? List.of()
                                : retroactivePeriodAdjustmentViewRepository
                                        .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                                                tenantId, maintenanceId,
                                                view.getRetroactivePeriodRecalculationId()),
                        snapshotViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                                .orElse(null)));
    }

    private Specification<MaintenanceView> specification(
            String tenantId, MaintenanceCaseSearchCriteria criteria) {
        return (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            predicates.add(builder.isTrue(root.get("independentCase")));
            predicates.add(builder.isTrue(root.get("initializationCompleted")));
            addEqual(predicates, builder, root.get("maintenanceId"), criteria.maintenanceId());
            addEqual(predicates, builder, root.get("policyNumber"), criteria.policyNumber());
            addEqual(predicates, builder, root.get("customerId"), criteria.customerId());
            addEqual(predicates, builder, root.get("source"), criteria.source());
            addEqual(predicates, builder, root.get("status"), criteria.status());
            addEqual(predicates, builder, root.get("createdBy"), criteria.operatorId());
            if (criteria.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createTime"), criteria.createdFrom()));
            }
            if (criteria.createdTo() != null) {
                predicates.add(builder.lessThan(root.get("createTime"), criteria.createdTo()));
            }
            if (hasText(criteria.itemCode())) {
                Subquery<String> itemSubquery = query.subquery(String.class);
                var item = itemSubquery.from(MaintenanceCaseItemView.class);
                itemSubquery.select(item.get("maintenanceId")).where(
                        builder.equal(item.get("tenantId"), tenantId),
                        builder.equal(item.get("itemCode"), criteria.itemCode().trim()));
                predicates.add(root.get("maintenanceId").in(itemSubquery));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private <T> void addEqual(
            List<jakarta.persistence.criteria.Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Path<T> path,
            T value) {
        if (value instanceof String text) {
            if (hasText(text)) {
                predicates.add(builder.equal(path, text.trim()));
            }
        } else if (value != null) {
            predicates.add(builder.equal(path, value));
        }
    }

    private MaintenanceCaseSummaryQueryResult toSummary(MaintenanceView view, List<String> itemCodes) {
        return new MaintenanceCaseSummaryQueryResult(
                view.getMaintenanceId(), view.getPolicyId(), view.getPolicyNumber(), view.getCustomerId(),
                itemCodes, view.getSource(), view.getStatus(), view.getEffectStatus(), view.getCreatedBy(),
                view.getCreateTime(), view.getUpdateTime());
    }

    private MaintenanceCaseDetailQueryResult toDetail(
            MaintenanceView view,
            List<MaintenanceCaseItemView> items,
            List<MaintenanceFieldChangeView> fieldChanges,
            List<MaintenanceWorkflowTaskView> workflowTasks,
            List<MaintenanceRetroactiveImpactItemView> retroactiveImpactItems,
            List<MaintenanceRetroactivePeriodAdjustmentView> retroactivePeriodAdjustments,
            MaintenanceSnapshotView snapshots) {
        return new MaintenanceCaseDetailQueryResult(
                view.getMaintenanceId(), view.getPolicyId(), view.getPolicyNumber(), view.getCustomerId(),
                view.getProductId(), view.getProductVersion(), view.getPlanVersion(),
                view.getPolicyBaselineVersion(), view.getBusinessEffectiveAt(), view.getSource(), view.getStatus(),
                view.getEffectStatus(), toEffectCompensation(view), toEffectSchedule(view),
                toRetroactiveImpactAnalysis(view, retroactiveImpactItems),
                toRetroactivePeriodRecalculation(view, retroactivePeriodAdjustments), view.getEffectiveTimeType(),
                view.getSpecificEffectiveDate(), view.getDescription(),
                view.getCreatedBy(), view.getCreateTime(), view.getUpdatedBy(), view.getUpdateTime(),
                items.stream().map(this::toItem).toList(),
                workflowTasks.stream().map(this::toWorkflowTask).toList(),
                fieldChanges.stream().map(this::toFieldChange).toList(),
                toSnapshotSet(snapshots));
    }

    private EffectCompensationQueryResult toEffectCompensation(MaintenanceView view) {
        if (view.getEffectCompensationId() == null) {
            return null;
        }
        return new EffectCompensationQueryResult(
                view.isEffectCompensationRequired(), view.getEffectCompensationId(),
                view.getEffectCompensationRequestId(), view.getEffectCompensationEndorsementNo(),
                view.getEffectCompensationPolicyVersion(), view.getEffectCompensationApplicationHash(),
                view.getEffectCompensationReason(), view.getEffectCompensationRecordedAt(),
                view.getEffectCompensationResolvedAt(), view.getEffectCompensationResolvedBy());
    }

    private EffectScheduleQueryResult toEffectSchedule(MaintenanceView view) {
        if (view.getEffectScheduleId() == null) {
            return null;
        }
        return new EffectScheduleQueryResult(
                view.getEffectScheduleId(), view.getEffectScheduleStatus(), view.getEffectScheduleTenantZoneId(),
                view.getEffectScheduleNextExecutionAt(), view.getEffectScheduleAttemptCount(),
                view.getEffectScheduleLastAttemptId(), view.getEffectScheduleLastAttemptAt(),
                view.getEffectScheduleLastErrorCode(), view.getEffectScheduleLastErrorMessage(),
                view.getEffectScheduleCreatedAt(), view.getEffectScheduleUpdatedAt());
    }

    private RetroactiveImpactAnalysisQueryResult toRetroactiveImpactAnalysis(
            MaintenanceView view,
            List<MaintenanceRetroactiveImpactItemView> items) {
        if (view.getRetroactiveImpactAnalysisId() == null) {
            return null;
        }
        List<MaintenanceRetroactiveImpactDomain> domains = hasText(view.getRetroactiveImpactCoveredDomains())
                ? Arrays.stream(view.getRetroactiveImpactCoveredDomains().split(","))
                        .map(MaintenanceRetroactiveImpactDomain::valueOf)
                        .toList()
                : List.of();
        List<RetroactiveImpactItemQueryResult> itemResults = items.stream()
                .sorted(Comparator
                        .comparing(MaintenanceRetroactiveImpactItemView::getSeverity).reversed()
                        .thenComparing(item -> item.getSourceDomain().getCode())
                        .thenComparing(MaintenanceRetroactiveImpactItemView::getOccurredAt)
                        .thenComparing(MaintenanceRetroactiveImpactItemView::getItemId))
                .map(this::toRetroactiveImpactItem)
                .toList();
        return new RetroactiveImpactAnalysisQueryResult(
                view.getRetroactiveImpactAnalysisId(), view.getRetroactiveImpactAnalysisVersion(),
                view.getRetroactiveImpactOperationId(), view.getRetroactiveImpactRequestHash(),
                view.getRetroactiveImpactScopeFrom(), view.getRetroactiveImpactScopeTo(),
                view.getRetroactiveImpactStatus(), domains, view.getRetroactiveImpactItemCount(),
                view.getRetroactiveImpactBlockingCount(), view.getRetroactiveImpactPendingCount(),
                view.getRetroactiveImpactEvidenceVersion(), view.getRetroactiveImpactResultHash(),
                view.getRetroactiveImpactFailureCode(), view.getRetroactiveImpactFailureMessage(),
                view.getRetroactiveImpactStartedAt(), view.getRetroactiveImpactCompletedAt(),
                view.getRetroactiveImpactUpdatedAt(), itemResults);
    }

    private RetroactiveImpactItemQueryResult toRetroactiveImpactItem(
            MaintenanceRetroactiveImpactItemView view) {
        return new RetroactiveImpactItemQueryResult(
                view.getItemId(), view.getSourceDomain(), view.getImpactType(), view.getReferenceId(),
                view.getReferenceNumber(), view.getOccurredAt(), view.getSourceStatus(), view.getAmount(),
                view.getCurrency(), view.getSeverity(), view.getHandlingStatus(), view.getSummary(),
                view.getEvidenceVersion(), view.getEvidenceHash());
    }

    private RetroactivePeriodRecalculationQueryResult toRetroactivePeriodRecalculation(
            MaintenanceView view,
            List<MaintenanceRetroactivePeriodAdjustmentView> periods) {
        if (view.getRetroactivePeriodRecalculationId() == null) {
            return null;
        }
        return new RetroactivePeriodRecalculationQueryResult(
                view.getRetroactivePeriodRecalculationId(), value(view.getRetroactivePeriodRecalculationVersion()),
                view.getRetroactivePeriodRecalculationOperationId(),
                view.getRetroactivePeriodRecalculationRequestHash(), view.getRetroactivePeriodAnalysisId(),
                value(view.getRetroactivePeriodAnalysisVersion()), view.getRetroactivePeriodAnalysisResultHash(),
                view.getRetroactivePeriodRecalculationStatus(), view.getRetroactiveProductRecalculationId(),
                view.getRetroactiveProductRecalculationVersion(),
                view.getRetroactiveProductOriginalCalculationId(), view.getRetroactiveProductOriginalResultHash(),
                view.getRetroactiveProductReplacementCalculationId(),
                view.getRetroactiveProductReplacementResultHash(), view.getRetroactiveProductDirection(),
                view.getRetroactiveProductAmount(), view.getRetroactiveProductCurrency(),
                view.getRetroactiveProductInputHash(), view.getRetroactiveProductResultHash(),
                view.getRetroactiveProductCalculatedAt(), value(view.getRetroactivePeriodCount()),
                view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingStatus(),
                value(view.getRetroactiveBillingPostedCount()), value(view.getRetroactiveBillingReviewCount()),
                view.getRetroactiveBillingRequestHash(), view.getRetroactiveBillingResultHash(),
                view.getRetroactiveBillingAdjustedAt(), view.getRetroactivePeriodFailureCode(),
                view.getRetroactivePeriodFailureMessage(), view.getRetroactivePeriodStartedAt(),
                view.getRetroactivePeriodCompletedAt(), view.getRetroactivePeriodUpdatedAt(),
                toRetroactivePeriodResolution(view),
                periods.stream().map(this::toRetroactivePeriodAdjustment).toList());
    }

    private RetroactivePeriodResolutionQueryResult toRetroactivePeriodResolution(MaintenanceView view) {
        if (view.getRetroactivePeriodResolutionId() == null) {
            return null;
        }
        return new RetroactivePeriodResolutionQueryResult(
                view.getRetroactivePeriodResolutionId(), view.getRetroactivePeriodResolutionOperationId(),
                view.getRetroactivePeriodResolutionRequestHash(), view.getRetroactivePeriodResolutionStatus(),
                view.getRetroactiveBillingResolutionId(),
                view.getRetroactivePeriodResolutionSourceBatchHash(),
                view.getRetroactivePeriodResolutionTargetPeriod(),
                view.getRetroactivePeriodResolutionResolvedLineCount(),
                view.getRetroactivePeriodResolutionResultHash(), view.getRetroactivePeriodResolutionReason(),
                view.getRetroactivePeriodResolutionFailureCode(),
                view.getRetroactivePeriodResolutionFailureMessage(),
                view.getRetroactivePeriodResolutionStartedAt(),
                view.getRetroactivePeriodResolutionCompletedAt(),
                view.getRetroactivePeriodResolutionUpdatedAt());
    }

    private RetroactivePeriodAdjustmentQueryResult toRetroactivePeriodAdjustment(
            MaintenanceRetroactivePeriodAdjustmentView view) {
        return new RetroactivePeriodAdjustmentQueryResult(
                view.getPeriodId(), view.getSourceReferenceId(), view.getAccountingPeriod(),
                view.getPeriodStart(), view.getOriginalAmount(), view.getRecalculatedAmount(),
                view.getDirection(), view.getDifferenceAmount(), view.getCurrency(), view.getBillingStatus(),
                view.getSourceEvidenceHash(), view.getProductResultHash(), view.getBillingResultHash(),
                view.getTargetAccountingPeriod(), view.getResolutionStatus(), view.getPostingReference(),
                view.getResolutionResultHash());
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private WorkflowTaskQueryResult toWorkflowTask(MaintenanceWorkflowTaskView view) {
        return new WorkflowTaskQueryResult(
                view.getTaskId(), view.getItemCode(), view.getItemOrder(), view.getSequence(),
                view.getStepType(), view.getMode(), view.getConditionRuleCode(), view.getStatus(),
                view.getAssignedTo() == null
                        ? null
                        : new WorkflowAssignmentQueryResult(view.getAssignedTo(), view.getClaimedAt()),
                view.getRetryCount(),
                view.getFailureCode() == null
                        ? null
                        : new WorkflowFailureQueryResult(view.getFailureCode(), view.getFailureReason()),
                view.getConditionDecision() == null
                        ? null
                        : new WorkflowConditionEvidenceQueryResult(
                                view.getConditionRuleVersion(), view.getConditionInputHash(),
                                view.getConditionDecision(), view.getConditionReason(),
                                view.getConditionDecidedAt(), view.getConditionDecidedBy()),
                view.getReviewDecision() == null
                        ? null
                        : new WorkflowReviewEvidenceQueryResult(
                                view.getReviewMode(), view.getReviewDecision(),
                                view.getReviewPolicyCode(), view.getReviewPolicyVersion(),
                                view.getReviewContextHash(), reviewGates(view.getReviewGateEvidenceJson()),
                                view.getReviewComment(), view.getReviewDecidedAt(), view.getReviewDecidedBy()),
                view.getUnderwritingConclusion() == null
                        ? null
                        : new WorkflowUnderwritingEvidenceQueryResult(
                                view.getUnderwritingCaseId(), view.getUnderwritingRequestHash(),
                                view.getUnderwritingRuleVersion(), view.getUnderwritingModelVersion(),
                                view.getUnderwritingConclusion(),
                                stringList(view.getUnderwritingConditionsJson()),
                                view.getUnderwritingSummary(), view.getUnderwritingCompletedAt()),
                view.getPremiumQuoteStatus() == null
                        ? null
                        : new WorkflowPremiumQuoteEvidenceQueryResult(
                                view.getPremiumQuoteStatus(), view.getPremiumQuoteId(),
                                view.getPremiumQuoteVersion(), view.getPremiumQuoteRequestHash(),
                                view.getPremiumQuoteOriginalCalculationId(),
                                view.getPremiumQuoteOriginalResultHash(),
                                view.getPremiumQuoteReplacementCalculationId(),
                                view.getPremiumQuoteReplacementResultHash(),
                                view.getPremiumQuotePricingPlanVersion(),
                                view.getPremiumQuotePricingPlanHash(), view.getPremiumQuoteResultHash(),
                                view.getPremiumQuoteDetailSummary(), view.getPremiumQuoteDirection(),
                                view.getPremiumQuoteAmount(), view.getPremiumQuoteCurrency(),
                                view.getPremiumQuotedAt(), view.getPremiumQuoteValidUntil()),
                view.getBillingPostingStatus() == null
                        ? null
                        : new WorkflowBillingPostingEvidenceQueryResult(
                                view.getBillingPostingId(), view.getBillingAdjustmentId(),
                                view.getBillingResultHash(), view.getBillingPostingDirection(),
                                view.getBillingPostingAmount(), view.getBillingPostingCurrency(),
                                view.getBillingPostingStatus(), view.getBillingCommissionAdjustmentCount(),
                                view.getBillingPostedAt()),
                view.getFundSettlementStatus() == null
                        ? null
                        : new WorkflowFundSettlementEvidenceQueryResult(
                                view.getFundSettlementType(), view.getFundSettlementStatus(),
                                view.getFundSourcePostingId(), view.getFundSettlementInstructionId(),
                                view.getFundSettlementOrderId(), view.getFundSettlementExternalStatus(),
                                view.getFundSettlementAmount(), view.getFundSettlementCurrency(),
                                view.getFundSettlementFailureCode(), view.getFundSettlementFailureMessage(),
                                view.getFundSettlementRecordedAt()),
                toEffectEvidence(view),
                view.getLastOperationId() == null
                        ? null
                        : new WorkflowOperationQueryResult(
                                view.getLastOperationId(), view.getLastOperationAction(),
                                view.getLastOperationHash(), view.getLastEvidenceVersion(),
                                view.getLastEvidenceHash(), view.getLastResultCode(),
                                view.getLastOperationReason(), view.getLastOperatedAt(),
                                view.getLastOperatedBy()));
    }

    private WorkflowEffectEvidenceQueryResult toEffectEvidence(MaintenanceWorkflowTaskView view) {
        if (view.getEffectRequestId() == null) {
            return null;
        }
        WorkflowEffectRequestEvidenceQueryResult request = new WorkflowEffectRequestEvidenceQueryResult(
                view.getEffectRequestId(), view.getEffectRequestHash(),
                view.getEffectExpectedPolicyVersion(), view.getEffectTimeType(),
                view.getEffectRequestedEffectiveAt(), view.getEffectProposedSnapshotHash(),
                view.getEffectRequestedAt());
        WorkflowPolicyApplicationEvidenceQueryResult application = view.getPolicyEndorsementNo() == null
                ? null
                : new WorkflowPolicyApplicationEvidenceQueryResult(
                        view.getEffectRequestId(), view.getPolicyEndorsementNo(),
                        view.getEffectExpectedPolicyVersion(), view.getPolicyActualVersion(),
                        view.getPolicyApplicationHash(),
                        new SnapshotReferenceQueryResult(
                                view.getAppliedSnapshotStorageKey(), view.getAppliedSnapshotHash(),
                                view.getAppliedSnapshotPolicyVersion(), view.getAppliedSnapshotCapturedAt()),
                        appliedFields(view.getAppliedFieldsJson()), view.getPolicyAppliedAt(),
                        view.getPolicyStateAction(), view.getPolicyStatusBefore(), view.getPolicyStatusAfter());
        return new WorkflowEffectEvidenceQueryResult(request, application);
    }

    private List<WorkflowAppliedFieldEvidenceQueryResult> appliedFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.parseArray(json, MaintenanceAppliedFieldEvidence.class).stream()
                .map(field -> new WorkflowAppliedFieldEvidenceQueryResult(
                        field.itemCode(), field.objectId(), field.fieldCode(),
                        field.dataType(), field.canonicalValue()))
                .toList();
    }

    private List<WorkflowReviewGateQueryResult> reviewGates(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.parseArray(json, MaintenanceReviewGateEvidence.class).stream()
                .map(gate -> new WorkflowReviewGateQueryResult(
                        gate.gate(), gate.passed(), gate.evidenceHash(), gate.detailCode()))
                .toList();
    }

    private List<String> stringList(String json) {
        return json == null || json.isBlank() ? List.of() : JSON.parseArray(json, String.class);
    }

    private ItemQueryResult toItem(MaintenanceCaseItemView view) {
        return new ItemQueryResult(
                view.getItemCode(), view.getItemName(), view.getItemCategory(),
                view.getConfigurationId(), view.getConfigurationVersion(), view.getConfigurationContentHash(),
                view.getOfferingId(), view.getOfferingVersion(), view.getOfferingContentHash(),
                view.getEvidenceResolvedAt(), view.getSelectedAt(), view.getWithdrawalStatus(),
                view.getWithdrawalOperationId(), view.getWithdrawalRequestHash(), view.getWithdrawalReason(),
                view.getWithdrawalSourcePostingId(), view.getWithdrawalSourceResultHash(),
                view.getWithdrawalSourceDirection(), view.getWithdrawalSourceFundStatus(),
                view.getWithdrawalReversalId(),
                view.getWithdrawalReversalResultHash(), view.getWithdrawalReversalDirection(),
                view.getWithdrawalAmount(), view.getWithdrawalCurrency(), view.getWithdrawalFundAction(),
                view.getWithdrawalFundStatus(), view.getWithdrawalFundRequestId(),
                view.getWithdrawalFundOrderId(), view.getWithdrawalFundExternalStatus(),
                view.getWithdrawalFailureCode(), view.getWithdrawalFailureMessage(),
                view.getWithdrawalRetryCount(), view.getWithdrawalRequestedAt(),
                view.getWithdrawalCompletedAt(), view.getWithdrawalRequestedBy(),
                view.getWithdrawalUpdatedBy());
    }

    private FieldChangeQueryResult toFieldChange(MaintenanceFieldChangeView view) {
        return new FieldChangeQueryResult(
                view.getItemCode(), view.getObjectId(), view.getFieldCode(), view.getLabelKey(), view.getDataType(),
                view.getBaseValue(), view.getCurrentValue(), view.getProposedValue(), view.getAppliedValue(),
                view.getConflictStatus(), view.getResolutionCode(), view.getConflictOperationId(),
                view.getConflictDetectedAt(), view.getConflictPolicyVersion(), view.getConflictEvidenceHash(),
                view.getResolutionOperationId(), view.getResolutionReason(), view.getResolutionEvidenceHash(),
                view.getResolvedBy(), view.getResolvedAt(), view.getSensitivity(), view.getMaskingPolicy(),
                view.getChangeTypeCode());
    }

    private SnapshotSetQueryResult toSnapshotSet(MaintenanceSnapshotView view) {
        if (view == null) {
            return new SnapshotSetQueryResult(null, null, null);
        }
        return new SnapshotSetQueryResult(
                reference(view.getBeforeStorageKey(), view.getBeforeContentHash(),
                        view.getBeforePolicyVersion(), view.getBeforeCapturedAt()),
                reference(view.getProposedStorageKey(), view.getProposedContentHash(),
                        view.getProposedPolicyVersion(), view.getProposedCapturedAt()),
                reference(view.getAppliedStorageKey(), view.getAppliedContentHash(),
                        view.getAppliedPolicyVersion(), view.getAppliedCapturedAt()));
    }

    private SnapshotReferenceQueryResult reference(
            String storageKey, String contentHash, Long policyVersion, String capturedAt) {
        return storageKey == null
                ? null
                : new SnapshotReferenceQueryResult(storageKey, contentHash, policyVersion, capturedAt);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
