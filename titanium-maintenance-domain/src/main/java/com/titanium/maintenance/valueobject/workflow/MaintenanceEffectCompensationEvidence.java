package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Policy 已成功但案件回执落库失败时保留的独立补偿事实。 */
public record MaintenanceEffectCompensationEvidence(
        String compensationId,
        String requestId,
        String endorsementNo,
        long actualPolicyVersion,
        String applicationHash,
        String failureReason,
        LocalDateTime recordedAt,
        String recordedBy) {

    public MaintenanceEffectCompensationEvidence {
        compensationId = requireText("compensationId", compensationId);
        requestId = requireText("requestId", requestId);
        endorsementNo = requireText("endorsementNo", endorsementNo);
        applicationHash = requireHash(applicationHash);
        failureReason = requireText("failureReason", failureReason);
        recordedBy = requireText("recordedBy", recordedBy);
        if (actualPolicyVersion <= 0 || recordedAt == null) {
            throw invalid("Policy 实际版本和补偿记录时间不能为空");
        }
    }

    private static String requireHash(String value) {
        String normalized = requireText("applicationHash", value).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw invalid("Policy 应用摘要必须为 SHA-256");
        }
        return normalized;
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String message) {
        return new MaintenanceValidationException("MaintenanceEffectCompensationEvidence", message);
    }
}
