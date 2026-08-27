package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全流程步骤启用方式。 */
@Getter
public enum MaintenanceStepMode implements BaseEnum {
    REQUIRED(1, "REQUIRED", "必需"),
    CONDITIONAL(2, "CONDITIONAL", "条件执行"),
    SKIPPED(3, "SKIPPED", "跳过");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceStepMode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
