package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 未来生效计划生命周期状态。 */
@Getter
public enum MaintenanceEffectScheduleStatus implements BaseEnum {
    ACTIVE(1, "ACTIVE", "待执行"),
    PAUSED(2, "PAUSED", "已暂停"),
    COMPLETED(3, "COMPLETED", "已完成"),
    FAILED(4, "FAILED", "执行失败");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceEffectScheduleStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
