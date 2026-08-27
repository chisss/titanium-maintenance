package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 发送 Policy 变更前冻结的生效请求证据。 */
public record MaintenanceEffectRequestEvidence(
        String requestId,
        String requestPayloadHash,
        long expectedPolicyVersion,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime requestedEffectiveAt,
        String proposedSnapshotHash,
        LocalDateTime requestedAt) {

    public MaintenanceEffectRequestEvidence {
        requestId = requireText("requestId", requestId);
        requestPayloadHash = requireHash("requestPayloadHash", requestPayloadHash);
        proposedSnapshotHash = requireHash("proposedSnapshotHash", proposedSnapshotHash);
        if (expectedPolicyVersion < 0) {
            throw invalid("expectedPolicyVersion", "期望保单版本不能为负数");
        }
        if (effectiveTimeType == null || requestedEffectiveAt == null || requestedAt == null) {
            throw invalid("effectTime", "生效模式、请求生效时间和请求时间不能为空");
        }
    }

    public String evidenceVersion() {
        return Long.toString(expectedPolicyVersion);
    }

    private static String requireHash(String field, String value) {
        String normalized = requireText(field, value).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw invalid(field, "摘要必须为 SHA-256");
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
        return new MaintenanceValidationException("MaintenanceEffectRequestEvidence", field, message);
    }
}
