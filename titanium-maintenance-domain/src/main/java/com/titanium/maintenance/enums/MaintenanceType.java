package com.titanium.maintenance.enums;

import com.titanium.maintenance.constant.MaintenanceConstants;
import com.titanium.maintenance.exception.MaintenanceValidationException;

public enum MaintenanceType {
    POLICY_HOLDER_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_HOLDER_CHANGE),
    BENEFICIARY_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_BENEFICIARY_CHANGE),
    PAYMENT_METHOD_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_PAYMENT_METHOD_CHANGE),
    ADDITIONAL_PAYMENT(MaintenanceConstants.MAINTENANCE_TYPE_ADDITIONAL_PAYMENT),
    REDUCTION_PAYMENT(MaintenanceConstants.MAINTENANCE_TYPE_REDUCTION_PAYMENT),
    POLICY_SUSPENSION(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_SUSPENSION),
    POLICY_RESUMPTION(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_RESUMPTION),
    POLICY_TERMINATION(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_TERMINATION),

    // 新增保全类型
    POLICY_INFO_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_INFO_CHANGE),
    POLICY_PERIOD_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_PERIOD_CHANGE),
    COVERAGE_AMOUNT_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_COVERAGE_AMOUNT_CHANGE),
    INSURED_INFO_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_INSURED_INFO_CHANGE),
    POLICY_REINSTATEMENT(MaintenanceConstants.MAINTENANCE_TYPE_POLICY_REINSTATEMENT),
    SUBJECT_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_SUBJECT_CHANGE),
    SMOKING_STATUS_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_SMOKING_STATUS_CHANGE),
    COVERAGE_CHANGE(MaintenanceConstants.MAINTENANCE_TYPE_COVERAGE_CHANGE);

    private final String value;

    MaintenanceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MaintenanceType fromValue(String value) {
        for (MaintenanceType type : MaintenanceType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new MaintenanceValidationException("MaintenanceType", "value", "无效的保全类型: " + value);
    }

    /**
     * 范式统一入口：按码值匹配枚举，未匹配返回 null（与 EffectiveTimeType/MaintenanceChangeType 的
     * fromCode 保持一致）。 注意：本枚举码值即 value 字段，为兼容既有 fromValue 调用方而保留 value 字段不变。
     */
    public static MaintenanceType fromCode(String code) {
        for (MaintenanceType type : MaintenanceType.values()) {
            if (type.value.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
