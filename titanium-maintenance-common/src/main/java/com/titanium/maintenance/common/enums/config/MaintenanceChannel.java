package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全受理渠道。 */
@Getter
public enum MaintenanceChannel implements BaseEnum {
    MANUAL(1, "MANUAL", "人工后台"),
    API(2, "API", "开放接口");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceChannel(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
