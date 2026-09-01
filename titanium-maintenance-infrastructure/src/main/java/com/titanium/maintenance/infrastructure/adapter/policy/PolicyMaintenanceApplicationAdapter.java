package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.request.maintenance.PolicyMaintenanceFieldChangeRequest;
import com.titanium.policy.api.request.maintenance.PolicyMaintenanceRetroactiveEvidenceRequest;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceRetroactiveEvidenceResponse;

import lombok.RequiredArgsConstructor;

/** Policy 正式保全应用 API 的防腐层适配器。 */
@Component
@RequiredArgsConstructor
public class PolicyMaintenanceApplicationAdapter implements PolicyMaintenanceApplicationPort {

    private final PolicyServiceClient policyServiceClient;

    @Override
    public ApplicationFact apply(ApplicationRequest request) {
        ApplyPolicyMaintenanceRequest remoteRequest = new ApplyPolicyMaintenanceRequest(
                request.requestId(), request.maintenanceCaseId(), request.expectedPolicyVersion(),
                request.requestPayloadHash(), request.proposedSnapshotHash(), request.effectiveTimeType(),
                request.effectiveAt(), request.changeSummary(), request.changes().stream()
                        .map(this::toRemoteField)
                        .toList(),
                request.stateAction(), request.stateReason(), request.terminationReason(),
                toRemoteRetroactiveEvidence(request.retroactiveEvidence()));
        ApiResponse<PolicyMaintenanceApplicationResponse> response = policyServiceClient.applyMaintenance(
                request.policyId(), remoteRequest, request.operatorId(), request.tenantId());
        PolicyMaintenanceApplicationResponse data = requireData(response);
        validateReceipt(request, data);
        return new ApplicationFact(
                data.requestId(), data.endorsementNo(), data.expectedPolicyVersion(), data.actualPolicyVersion(),
                data.applicationHash(),
                new AppliedSnapshot(
                        data.appliedSnapshot().storageKey(), data.appliedSnapshot().contentHash(),
                        data.appliedSnapshot().policyVersion(), data.appliedSnapshot().capturedAt()),
                data.appliedFields().stream()
                        .map(field -> new AppliedField(
                                field.itemCode(), field.objectId(), field.fieldCode(),
                                field.dataType(), field.canonicalValue()))
                        .toList(),
                data.appliedAt(), data.stateAction(), data.statusBefore(), data.statusAfter(),
                toRetroactiveEvidence(data.retroactiveEvidence()));
    }

    private PolicyMaintenanceFieldChangeRequest toRemoteField(FieldChange field) {
        return new PolicyMaintenanceFieldChangeRequest(
                field.itemCode(), field.objectId(), field.fieldCode(),
                field.dataType(), field.canonicalValue());
    }

    private PolicyMaintenanceApplicationResponse requireData(
            ApiResponse<PolicyMaintenanceApplicationResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw validation("Policy 正式保全应用 API 未返回成功事实");
        }
        return response.getData();
    }

    private void validateReceipt(ApplicationRequest request, PolicyMaintenanceApplicationResponse response) {
        if (!Objects.equals(request.requestId(), response.requestId())
                || request.expectedPolicyVersion() != response.expectedPolicyVersion()
                || response.actualPolicyVersion() <= response.expectedPolicyVersion()) {
            throw validation("Policy 回执的请求ID或版本勾稽失败");
        }
        if (response.endorsementNo() == null || response.endorsementNo().isBlank()
                || !isHash(response.applicationHash()) || response.appliedAt() == null
                || response.appliedSnapshot() == null || response.appliedFields() == null) {
            throw validation("Policy 回执缺少批单、摘要或实际快照");
        }
        if (response.appliedSnapshot().policyVersion() != response.actualPolicyVersion()
                || !isHash(response.appliedSnapshot().contentHash())
                || response.appliedSnapshot().storageKey() == null
                || response.appliedSnapshot().storageKey().isBlank()
                || response.appliedSnapshot().capturedAt() == null) {
            throw validation("Policy 实际快照与回执版本不一致");
        }
        List<String> requestedFields = request.changes().stream()
                .map(field -> signature(
                        field.itemCode(), field.objectId(), field.fieldCode(), field.dataType()))
                .sorted()
                .toList();
        List<String> appliedFields = response.appliedFields().stream()
                .map(field -> signature(
                        field.itemCode(), field.objectId(), field.fieldCode(), field.dataType()))
                .sorted()
                .toList();
        if (!requestedFields.equals(appliedFields)) {
            throw validation("Policy 实际字段的项目、对象、字段或类型与请求不一致");
        }
        if (response.stateAction() != request.stateAction()) {
            throw validation("Policy 回执的状态动作与请求不一致");
        }
        if (request.stateAction().changesStatus()
                && (response.statusBefore() == null || response.statusBefore().isBlank()
                        || response.statusAfter() == null || response.statusAfter().isBlank())) {
            throw validation("状态类 Policy 回执缺少变更前后状态");
        }
        if (!Objects.equals(request.retroactiveEvidence(), toRetroactiveEvidence(response.retroactiveEvidence()))) {
            throw validation("Policy 回执的追溯证据与请求不一致");
        }
    }

    private PolicyMaintenanceRetroactiveEvidenceRequest toRemoteRetroactiveEvidence(
            RetroactiveEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        return new PolicyMaintenanceRetroactiveEvidenceRequest(
                evidence.analysisId(), evidence.analysisVersion(), evidence.analysisResultHash(),
                evidence.periodRecalculationId(), evidence.periodRecalculationVersion(),
                evidence.productRecalculationId(), evidence.productRecalculationVersion(),
                evidence.productInputHash(), evidence.productResultHash(), evidence.billingBatchId(),
                evidence.billingBatchResultHash(), evidence.billingStatus(), evidence.billingResolutionId(),
                evidence.billingResolutionResultHash(), evidence.targetAccountingPeriod(),
                evidence.resolvedLineCount());
    }

    private RetroactiveEvidence toRetroactiveEvidence(
            PolicyMaintenanceRetroactiveEvidenceResponse evidence) {
        if (evidence == null) {
            return null;
        }
        return new RetroactiveEvidence(
                evidence.analysisId(), evidence.analysisVersion(), evidence.analysisResultHash(),
                evidence.periodRecalculationId(), evidence.periodRecalculationVersion(),
                evidence.productRecalculationId(), evidence.productRecalculationVersion(),
                evidence.productInputHash(), evidence.productResultHash(), evidence.billingBatchId(),
                evidence.billingBatchResultHash(), evidence.billingStatus(), evidence.billingResolutionId(),
                evidence.billingResolutionResultHash(), evidence.targetAccountingPeriod(),
                evidence.resolvedLineCount());
    }

    private String signature(String itemCode, String objectId, String fieldCode, String dataType) {
        return String.join("\n",
                Objects.toString(itemCode, ""), Objects.toString(objectId, ""),
                Objects.toString(fieldCode, ""), Objects.toString(dataType, ""));
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    private MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("PolicyMaintenanceApplicationAdapter", message);
    }
}
