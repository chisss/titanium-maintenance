package com.titanium.maintenance.valueobject.workflow;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 流程任务最后一次失败原因。 */
public record MaintenanceWorkflowFailure(String failureCode, String failureReason) {

    public MaintenanceWorkflowFailure {
        failureCode = requireText("failureCode", failureCode);
        failureReason = requireText("failureReason", failureReason);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowFailure", fieldName, "字段不能为空");
        }
        return value.trim();
    }
}
