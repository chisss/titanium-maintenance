package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全审核的执行方式。 */
@Getter
public enum MaintenanceReviewMode implements BaseEnum {
    MANUAL(1, "MANUAL", "人工审核"),
    AUTOMATIC(2, "AUTOMATIC", "自动审核");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceReviewMode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
