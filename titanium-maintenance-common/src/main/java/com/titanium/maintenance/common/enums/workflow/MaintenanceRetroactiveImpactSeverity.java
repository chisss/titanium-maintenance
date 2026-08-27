package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯影响项的操作优先级。 */
@Getter
public enum MaintenanceRetroactiveImpactSeverity implements BaseEnum {
    INFORMATIONAL(1, "INFORMATIONAL", "提示"),
    WARNING(2, "WARNING", "警告"),
    BLOCKING(3, "BLOCKING", "阻断");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactSeverity(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
