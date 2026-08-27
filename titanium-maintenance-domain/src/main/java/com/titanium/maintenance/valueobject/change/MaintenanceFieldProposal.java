package com.titanium.maintenance.valueobject.change;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 入站字段提案；值必须以明确类型和规范化文本表达。 */
public record MaintenanceFieldProposal(
        String objectId,
        String fieldCode,
        PolicyFieldDataType dataType,
        String canonicalValue) {

    public MaintenanceFieldProposal {
        objectId = normalize(objectId);
        fieldCode = requireText(fieldCode);
        if (objectId != null && objectId.length() > 128) {
            throw validation("objectId", "业务对象ID不能超过128字符");
        }
        if (fieldCode.length() > 128) {
            throw validation("fieldCode", "字段编码不能超过128字符");
        }
        if (canonicalValue != null && canonicalValue.length() > 32768) {
            throw validation("canonicalValue", "字段值不能超过32768字符");
        }
        if (dataType == null) {
            throw validation("dataType", "字段类型不能为空");
        }
        canonicalValue = new MaintenanceFieldValue(dataType, canonicalValue).canonicalValue();
    }

    public MaintenanceFieldValue value() {
        return new MaintenanceFieldValue(dataType, canonicalValue);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw validation("fieldCode", "字段编码不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceFieldProposal", fieldName, message);
    }
}
