package com.titanium.maintenance.configuration;

import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项流程步骤定义。 */
public record MaintenanceStepDefinition(int sequence, MaintenanceStepType stepType, MaintenanceStepMode mode,
        String conditionRuleCode) {

    public MaintenanceStepDefinition {
        if (sequence < 1) {
            throw new MaintenanceValidationException("MaintenanceStepDefinition", "sequence", "步骤顺序必须大于零");
        }
        if (stepType == null || mode == null) {
            throw new MaintenanceValidationException("MaintenanceStepDefinition", "步骤类型和启用方式不能为空");
        }
        conditionRuleCode = normalize(conditionRuleCode);
        if (mode == MaintenanceStepMode.CONDITIONAL && conditionRuleCode == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceStepDefinition", "conditionRuleCode", "条件步骤必须引用条件规则");
        }
        if (mode != MaintenanceStepMode.CONDITIONAL && conditionRuleCode != null) {
            throw new MaintenanceValidationException(
                    "MaintenanceStepDefinition", "conditionRuleCode", "非条件步骤不能配置条件规则");
        }
    }

    /** 创建必需步骤。 */
    public static MaintenanceStepDefinition required(int sequence, MaintenanceStepType stepType) {
        return new MaintenanceStepDefinition(sequence, stepType, MaintenanceStepMode.REQUIRED, null);
    }

    /** 创建明确跳过的步骤。 */
    public static MaintenanceStepDefinition skipped(int sequence, MaintenanceStepType stepType) {
        return new MaintenanceStepDefinition(sequence, stepType, MaintenanceStepMode.SKIPPED, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
