package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯影响项的处理状态；M5-05B 新发现项固定为待处理。 */
@Getter
public enum MaintenanceRetroactiveImpactItemStatus implements BaseEnum {
    PENDING(1, "PENDING", "待处理"),
    RESOLVED(2, "RESOLVED", "已解决"),
    WAIVED(3, "WAIVED", "已豁免");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactItemStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
