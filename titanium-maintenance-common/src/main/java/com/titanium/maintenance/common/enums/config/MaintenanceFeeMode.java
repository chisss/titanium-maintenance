package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全项收退费要求。 */
@Getter
public enum MaintenanceFeeMode implements BaseEnum {
    REQUIRED(1, "REQUIRED", "必须收退费"),
    OPTIONAL(2, "OPTIONAL", "条件收退费"),
    NONE(3, "NONE", "无收退费");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceFeeMode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
