package com.titanium.maintenance.common.enums.change;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 字段顺序外冲突的显式解决动作。 */
@Getter
public enum MaintenanceFieldConflictResolutionAction implements BaseEnum {
    USE_CURRENT(1, "USE_CURRENT", "采用当前值"),
    USE_PROPOSED(2, "USE_PROPOSED", "采用拟变更值"),
    REENTER(3, "REENTER", "重新录入");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceFieldConflictResolutionAction(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
