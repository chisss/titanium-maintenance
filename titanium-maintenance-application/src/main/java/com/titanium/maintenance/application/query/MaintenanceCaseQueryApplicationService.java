package com.titanium.maintenance.application.query;

import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.FieldChangeQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAppliedFieldEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPolicyApplicationEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowTaskQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.query.service.MaintenanceCaseQueryService;

import lombok.RequiredArgsConstructor;

/** 独立保全案件查询应用门面，统一执行敏感字段脱敏。 */
@Service
@RequiredArgsConstructor
public class MaintenanceCaseQueryApplicationService {

    private final MaintenanceCaseQueryService queryService;
    private final MaintenanceCaseFieldMasker fieldMasker;

    public MaintenanceCasePageQueryResult search(
            String tenantId, MaintenanceCaseSearchCriteria criteria) {
        if (criteria.createdFrom() != null && criteria.createdTo() != null
                && !criteria.createdFrom().isBefore(criteria.createdTo())) {
            throw new MaintenanceValidationException(
                    "MaintenanceCaseSearchCriteria", "createdTo", "查询结束时间必须晚于开始时间");
        }
        return queryService.search(tenantId, criteria);
    }

    public MaintenanceCaseDetailQueryResult findDetail(
            String tenantId, String maintenanceId, boolean sensitiveDetailsVisible) {
        MaintenanceCaseDetailQueryResult detail = queryService.findDetail(tenantId, maintenanceId)
                .orElseThrow(MaintenanceNotFoundException::new);
        return new MaintenanceCaseDetailQueryResult(
                detail.maintenanceId(), detail.policyId(), detail.policyNumber(), detail.customerId(),
                detail.productId(), detail.productVersion(), detail.planVersion(), detail.policyBaselineVersion(),
                detail.businessEffectiveAt(), detail.source(), detail.status(), detail.effectStatus(),
                detail.effectCompensation(), detail.effectSchedule(), detail.retroactiveImpactAnalysis(),
                detail.retroactivePeriodRecalculation(),
                detail.effectiveTimeType(),
                detail.specificEffectiveDate(), detail.description(), detail.createdBy(), detail.createdAt(),
                detail.updatedBy(), detail.updatedAt(), detail.items(),
                detail.workflowTasks().stream()
                        .map(task -> mask(task, detail.fieldChanges(), sensitiveDetailsVisible))
                        .toList(),
                detail.fieldChanges().stream()
                        .map(field -> mask(field, sensitiveDetailsVisible))
                        .toList(),
                detail.snapshots());
    }

    private WorkflowTaskQueryResult mask(
            WorkflowTaskQueryResult task,
            List<FieldChangeQueryResult> fields,
            boolean sensitiveDetailsVisible) {
        WorkflowEffectEvidenceQueryResult effect = task.effectEvidence();
        if (effect == null || effect.application() == null) {
            return task;
        }
        WorkflowPolicyApplicationEvidenceQueryResult application = effect.application();
        WorkflowPolicyApplicationEvidenceQueryResult maskedApplication =
                new WorkflowPolicyApplicationEvidenceQueryResult(
                        application.requestId(), application.endorsementNo(),
                        application.expectedPolicyVersion(), application.actualPolicyVersion(),
                        application.applicationHash(), application.appliedSnapshot(),
                        application.appliedFields().stream()
                                .map(field -> mask(field, fields, sensitiveDetailsVisible))
                                .toList(),
                        application.appliedAt());
        return new WorkflowTaskQueryResult(
                task.taskId(), task.itemCode(), task.itemOrder(), task.sequence(),
                task.stepType(), task.mode(), task.conditionRuleCode(), task.status(),
                task.assignment(), task.retryCount(), task.failure(), task.conditionEvidence(),
                task.reviewEvidence(), task.underwritingEvidence(), task.premiumQuoteEvidence(),
                task.billingPostingEvidence(), task.fundSettlementEvidence(),
                new WorkflowEffectEvidenceQueryResult(effect.request(), maskedApplication),
                task.lastOperation());
    }

    private WorkflowAppliedFieldEvidenceQueryResult mask(
            WorkflowAppliedFieldEvidenceQueryResult applied,
            List<FieldChangeQueryResult> fields,
            boolean sensitiveDetailsVisible) {
        FieldChangeQueryResult descriptor = fields.stream()
                .filter(field -> field.itemCode().equals(applied.itemCode())
                        && field.objectId().equals(applied.objectId())
                        && field.fieldCode().equals(applied.fieldCode()))
                .findFirst()
                .orElse(null);
        return new WorkflowAppliedFieldEvidenceQueryResult(
                applied.itemCode(), applied.objectId(), applied.fieldCode(), applied.dataType(),
                fieldMasker.mask(
                        applied.canonicalValue(),
                        descriptor == null ? null : descriptor.sensitivity(),
                        descriptor == null ? null : descriptor.maskingPolicy(),
                        sensitiveDetailsVisible));
    }

    private FieldChangeQueryResult mask(
            FieldChangeQueryResult field, boolean sensitiveDetailsVisible) {
        return new FieldChangeQueryResult(
                field.itemCode(), field.objectId(), field.fieldCode(), field.labelKey(), field.dataType(),
                fieldMasker.mask(field.baseValue(), field.sensitivity(), field.maskingPolicy(), sensitiveDetailsVisible),
                fieldMasker.mask(
                        field.currentValue(), field.sensitivity(), field.maskingPolicy(), sensitiveDetailsVisible),
                fieldMasker.mask(
                        field.proposedValue(), field.sensitivity(), field.maskingPolicy(), sensitiveDetailsVisible),
                fieldMasker.mask(
                        field.appliedValue(), field.sensitivity(), field.maskingPolicy(), sensitiveDetailsVisible),
                field.conflictStatus(), field.resolutionCode(), field.conflictOperationId(),
                field.conflictDetectedAt(), field.conflictPolicyVersion(), field.conflictEvidenceHash(),
                field.resolutionOperationId(), field.resolutionReason(), field.resolutionEvidenceHash(),
                field.resolvedBy(), field.resolvedAt(), field.sensitivity(), field.maskingPolicy(),
                field.changeTypeCode());
    }
}
