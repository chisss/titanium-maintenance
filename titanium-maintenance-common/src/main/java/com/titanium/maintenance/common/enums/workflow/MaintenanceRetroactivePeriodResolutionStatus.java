package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 关闭会计期间处理状态，与期间重算状态正交。 */
@Getter
public enum MaintenanceRetroactivePeriodResolutionStatus implements BaseEnum {
    RESOLVING(1, "RESOLVING", "处理中"),
    COMPLETED(2, "COMPLETED", "处理完成"),
    FAILED(3, "FAILED", "处理失败");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactivePeriodResolutionStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenanceRetroactivePeriodResolutionStatus fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceRetroactivePeriodResolutionStatus.class, code);
    }
}
