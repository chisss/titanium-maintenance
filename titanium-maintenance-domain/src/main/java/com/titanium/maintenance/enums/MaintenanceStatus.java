package com.titanium.maintenance.enums;

import com.titanium.maintenance.constant.MaintenanceConstants;
import com.titanium.maintenance.exception.MaintenanceValidationException;

public enum MaintenanceStatus {
    PENDING(MaintenanceConstants.MAINTENANCE_STATUS_PENDING),
    PROCESSING(MaintenanceConstants.MAINTENANCE_STATUS_PROCESSING),
    APPROVED(MaintenanceConstants.MAINTENANCE_STATUS_APPROVED),
    REJECTED(MaintenanceConstants.MAINTENANCE_STATUS_REJECTED),
    COMPLETED(MaintenanceConstants.MAINTENANCE_STATUS_COMPLETED);

    private final String value;

    MaintenanceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MaintenanceStatus fromValue(String value) {
        for (MaintenanceStatus status : MaintenanceStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new MaintenanceValidationException("MaintenanceStatus", "value", "无效的保全状态: " + value);
    }

    /**
     * 范式统一入口：按码值匹配枚举，未匹配返回 null（与 EffectiveTimeType/MaintenanceChangeType 的 fromCode 保持一致）。
     * 注意：本枚举码值即 value 字段，为兼容既有 fromValue 调用方而保留 value 字段不变。
     */
    public static MaintenanceStatus fromCode(String code) {
        for (MaintenanceStatus status : MaintenanceStatus.values()) {
            if (status.value.equals(code)) {
                return status;
            }
        }
        return null;
    }
}