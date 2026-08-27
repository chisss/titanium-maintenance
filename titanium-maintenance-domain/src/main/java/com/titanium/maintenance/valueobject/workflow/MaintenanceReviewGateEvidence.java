package com.titanium.maintenance.valueobject.workflow;

import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 单个自动审核门禁的权威证据摘要。 */
public record MaintenanceReviewGateEvidence(
        MaintenanceReviewGate gate,
        boolean passed,
        String evidenceHash,
        String detailCode) {

    public MaintenanceReviewGateEvidence {
        if (gate == null) {
            throw validation("gate", "审核门禁不能为空");
        }
        evidenceHash = requireText("evidenceHash", evidenceHash).toLowerCase();
        detailCode = requireText("detailCode", detailCode);
        if (!evidenceHash.matches("[0-9a-f]{64}")) {
            throw validation("evidenceHash", "门禁证据摘要必须为SHA-256");
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceReviewGateEvidence", fieldName, message);
    }
}
