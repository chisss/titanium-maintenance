package com.titanium.maintenance.configuration;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** 保全项可见与可编辑字段规则。 */
public record MaintenanceFieldRule(String fieldCode, boolean required, boolean visible, boolean editable,
        boolean allowClear, String conditionRuleCode, PolicyFieldValueType expectedValueType) {

    public MaintenanceFieldRule {
        if (!hasText(fieldCode)) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "fieldCode", "字段编码不能为空");
        }
        if (editable && !visible) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "editable", "可编辑字段必须同时可见");
        }
        if (required && !editable) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "required", "必填字段必须可编辑");
        }
        conditionRuleCode = normalize(conditionRuleCode);
    }

    /** 兼容 Phase 1 未声明字段类型的辅助构造器。 */
    public MaintenanceFieldRule(String fieldCode, boolean required, boolean visible, boolean editable,
            boolean allowClear, String conditionRuleCode) {
        this(fieldCode, required, visible, editable, allowClear, conditionRuleCode, null);
    }

    /** 创建常用的可编辑字段规则。 */
    public static MaintenanceFieldRule editable(String fieldCode, boolean required, boolean allowClear) {
        return new MaintenanceFieldRule(fieldCode, required, true, true, allowClear, null);
    }

    /** 创建声明 Policy 字段值类型的可编辑规则。 */
    public static MaintenanceFieldRule editable(String fieldCode, boolean required, boolean allowClear,
            PolicyFieldValueType expectedValueType) {
        return new MaintenanceFieldRule(
                fieldCode, required, true, true, allowClear, null, expectedValueType);
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
