package com.titanium.maintenance.valueobject.change;

import java.time.LocalDate;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** 字段提案时冻结的 Policy 字段描述与敏感掩码元数据。 */
public record MaintenanceFieldDescriptorSnapshot(
        String fieldCode,
        PolicyFieldObjectType objectType,
        PolicyFieldValueType valueType,
        String labelKey,
        boolean collection,
        String objectIdentityField,
        boolean readable,
        boolean proposable,
        boolean clearable,
        boolean requiresObjectId,
        String changeTypeCode,
        PolicyFieldSensitivityLevel sensitivity,
        PolicyFieldMaskingPolicy maskingPolicy,
        LocalDate deprecatedAt) {

    public MaintenanceFieldDescriptorSnapshot {
        fieldCode = requireText("fieldCode", fieldCode);
        labelKey = requireText("labelKey", labelKey);
        objectIdentityField = normalize(objectIdentityField);
        changeTypeCode = normalize(changeTypeCode);
        if (objectType == null || valueType == null || sensitivity == null || maskingPolicy == null) {
            throw validation("字段类型和敏感元数据不能为空");
        }
        if (collection && objectIdentityField == null) {
            throw validation("集合字段必须冻结稳定业务对象标识字段");
        }
        if (!collection && objectIdentityField != null) {
            throw validation("非集合字段不能包含业务对象标识字段");
        }
        if (requiresObjectId && !collection) {
            throw validation("仅集合字段可以要求业务对象标识");
        }
        if (proposable && changeTypeCode == null) {
            throw validation("可提案字段必须包含业务变更类别");
        }
        if (sensitivity.requiresMasking() && maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
            throw validation("敏感字段必须包含掩码策略");
        }
    }

    public boolean activeAt(LocalDate businessDate) {
        return businessDate != null && (deprecatedAt == null || businessDate.isBefore(deprecatedAt));
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldDescriptorSnapshot", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("MaintenanceFieldDescriptorSnapshot", message);
    }
}
