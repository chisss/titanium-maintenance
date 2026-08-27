package com.titanium.maintenance.valueobject.workflow;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Policy 回执中的单字段实际生效值。 */
public record MaintenanceAppliedFieldEvidence(
        String itemCode,
        String objectId,
        String fieldCode,
        PolicyFieldDataType dataType,
        String canonicalValue) {

    public MaintenanceAppliedFieldEvidence {
        itemCode = requireText("itemCode", itemCode);
        objectId = requireText("objectId", objectId);
        fieldCode = requireText("fieldCode", fieldCode);
        if (dataType == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceAppliedFieldEvidence", "dataType", "字段类型不能为空");
        }
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceAppliedFieldEvidence", field, "字段不能为空");
        }
        return value.trim();
    }
}
