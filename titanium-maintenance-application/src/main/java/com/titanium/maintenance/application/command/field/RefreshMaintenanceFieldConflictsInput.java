package com.titanium.maintenance.application.command.field;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 刷新案件字段冲突的应用层输入。 */
public record RefreshMaintenanceFieldConflictsInput(
        String maintenanceId,
        String operationId,
        String operatorId,
        String tenantId) {

    public RefreshMaintenanceFieldConflictsInput {
        maintenanceId = requireText("maintenanceId", maintenanceId);
        operationId = requireText("operationId", operationId);
        operatorId = requireText("operatorId", operatorId);
        tenantId = requireText("tenantId", tenantId);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "RefreshMaintenanceFieldConflictsInput", fieldName, "字段不能为空");
        }
        return value.trim();
    }
}
