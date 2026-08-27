package com.titanium.maintenance.configuration.control;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项材料要求。 */
public record MaintenanceMaterialRequirement(String materialCode, boolean required, String conditionRuleCode) {

    public MaintenanceMaterialRequirement {
        materialCode = requireText("materialCode", materialCode);
        conditionRuleCode = normalize(conditionRuleCode);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceMaterialRequirement", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
