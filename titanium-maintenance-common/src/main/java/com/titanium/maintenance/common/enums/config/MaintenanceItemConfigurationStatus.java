package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全项配置生命周期状态。 */
@Getter
public enum MaintenanceItemConfigurationStatus implements BaseEnum {
    DRAFT(1, "DRAFT", "草稿"),
    PENDING_APPROVAL(2, "PENDING_APPROVAL", "待审批"),
    APPROVED(3, "APPROVED", "已审批"),
    PUBLISHED(4, "PUBLISHED", "已发布"),
    RETIRED(5, "RETIRED", "已退役");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceItemConfigurationStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
