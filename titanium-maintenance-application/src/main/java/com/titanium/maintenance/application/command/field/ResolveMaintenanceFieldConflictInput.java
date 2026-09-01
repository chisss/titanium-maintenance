package com.titanium.maintenance.application.command.field;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 显式解决单个字段冲突的应用层输入。 */
public record ResolveMaintenanceFieldConflictInput(
        String maintenanceId,
        String operationId,
        String itemCode,
        String objectId,
        String fieldCode,
        MaintenanceFieldConflictResolutionAction action,
        PolicyFieldDataType dataType,
        String canonicalValue,
        String reason,
        String operatorId,
        String tenantId) {

    public ResolveMaintenanceFieldConflictInput {
        maintenanceId = requireText("maintenanceId", maintenanceId);
        operationId = requireText("operationId", operationId);
        itemCode = requireText("itemCode", itemCode);
        objectId = requireText("objectId", objectId);
        fieldCode = requireText("fieldCode", fieldCode);
        if (action == null) {
            throw validation("action", "冲突解决动作不能为空");
        }
        reason = requireText("reason", reason);
        operatorId = requireText("operatorId", operatorId);
        tenantId = requireText("tenantId", tenantId);
        if (action == MaintenanceFieldConflictResolutionAction.REENTER && dataType == null) {
            throw validation("dataType", "重新录入必须提供字段类型");
        }
        if (action != MaintenanceFieldConflictResolutionAction.REENTER
                && (dataType != null || canonicalValue != null)) {
            throw validation("canonicalValue", "只有重新录入允许提供字段值");
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException(
                "ResolveMaintenanceFieldConflictInput", fieldName, message);
    }
}
