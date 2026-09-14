package com.titanium.maintenance.valueobject.endorsement;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 批单中单个业务字段的批改前后取值值对象。
 *
 * @param objectId    业务对象标识（集合型保全项的定位键，如被保险人ID；标量字段为空串）
 * @param fieldCode   字段编码
 * @param beforeValue 批改前值（规范化文本，可为空表示原值为空）
 * @param afterValue  批改后值（规范化文本，可为空表示变更为空值）
 */
public record EndorsementFieldChange(String objectId, String fieldCode, String beforeValue, String afterValue) {

    public EndorsementFieldChange {
        if (fieldCode == null || fieldCode.isBlank()) {
            throw new MaintenanceValidationException("EndorsementFieldChange", "fieldCode", "字段编码不能为空");
        }
        objectId = objectId == null ? "" : objectId;
    }
}
