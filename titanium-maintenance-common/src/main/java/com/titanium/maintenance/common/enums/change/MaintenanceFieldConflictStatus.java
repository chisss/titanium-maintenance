package com.titanium.maintenance.common.enums.change;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全字段的顺序外冲突状态。 */
@Getter
public enum MaintenanceFieldConflictStatus implements BaseEnum {
    NONE(1, "NONE", "无冲突"),
    DETECTED(2, "DETECTED", "待解决"),
    RESOLVED(3, "RESOLVED", "已解决");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceFieldConflictStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
