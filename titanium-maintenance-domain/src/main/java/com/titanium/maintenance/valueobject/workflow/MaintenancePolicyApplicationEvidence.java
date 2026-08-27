package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;

/** Policy 成功应用保全变更后返回的权威回执。 */
public record MaintenancePolicyApplicationEvidence(
        String requestId,
        String endorsementNo,
        long expectedPolicyVersion,
        long actualPolicyVersion,
        String applicationHash,
        MaintenanceSnapshotReference appliedSnapshot,
        List<MaintenanceAppliedFieldEvidence> appliedFields,
        LocalDateTime appliedAt,
        PolicyMaintenanceAction stateAction,
        String statusBefore,
        String statusAfter) {

    public MaintenancePolicyApplicationEvidence {
        requestId = requireText("requestId", requestId);
        endorsementNo = requireText("endorsementNo", endorsementNo);
        applicationHash = requireHash(applicationHash);
        if (expectedPolicyVersion < 0 || actualPolicyVersion <= expectedPolicyVersion) {
            throw invalid("policyVersion", "实际保单版本必须大于期望版本");
        }
        if (appliedSnapshot == null || appliedSnapshot.policyVersion() != actualPolicyVersion) {
            throw invalid("appliedSnapshot", "实际快照版本必须等于 Policy 回执版本");
        }
        stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
        if (appliedFields == null || appliedFields.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("appliedFields", "Policy 回执实际字段集合不能为 null 或包含空项");
        }
        appliedFields = List.copyOf(appliedFields);
        if (appliedFields.isEmpty() && !stateAction.changesStatus()) {
            throw invalid("appliedFields", "字段型 Policy 回执必须包含实际字段值");
        }
        if (stateAction.changesStatus()
                && (statusBefore == null || statusBefore.isBlank()
                        || statusAfter == null || statusAfter.isBlank())) {
            throw invalid("policyStatus", "状态类 Policy 回执必须包含变更前后状态");
        }
        if (appliedAt == null) {
            throw invalid("appliedAt", "Policy 应用时间不能为空");
        }
    }

    /** 兼容 M5-02 字段型回执事实。 */
    public MaintenancePolicyApplicationEvidence(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            MaintenanceSnapshotReference appliedSnapshot,
            List<MaintenanceAppliedFieldEvidence> appliedFields,
            LocalDateTime appliedAt) {
        this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion, applicationHash,
                appliedSnapshot, appliedFields, appliedAt, PolicyMaintenanceAction.NONE, null, null);
    }

    public String evidenceVersion() {
        return Long.toString(actualPolicyVersion);
    }

    private static String requireHash(String value) {
        String normalized = requireText("applicationHash", value).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw invalid("applicationHash", "应用摘要必须为 SHA-256");
        }
        return normalized;
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenancePolicyApplicationEvidence", field, message);
    }
}
