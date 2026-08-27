package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 条件规则的版本化可解释结论。 */
public record MaintenanceWorkflowConditionEvidence(
        String ruleVersion,
        String inputHash,
        MaintenanceWorkflowConditionDecision decision,
        String reason,
        LocalDateTime decidedAt,
        String decidedBy) {

    public MaintenanceWorkflowConditionEvidence {
        ruleVersion = requireText("ruleVersion", ruleVersion);
        inputHash = requireText("inputHash", inputHash).toLowerCase();
        reason = requireText("reason", reason);
        decidedBy = requireText("decidedBy", decidedBy);
        if (!inputHash.matches("[0-9a-f]{64}")) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowConditionEvidence", "inputHash", "输入摘要必须为SHA-256");
        }
        if (decision == null || decidedAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowConditionEvidence", "结论和判定时间不能为空");
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowConditionEvidence", fieldName, "字段不能为空");
        }
        return value.trim();
    }
}
