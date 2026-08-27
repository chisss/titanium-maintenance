package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 自动审核编排的可观察结果。 */
@Getter
public enum MaintenanceAutomaticReviewOutcome implements BaseEnum {
    APPROVED(1, "APPROVED", "自动通过"),
    MANUAL_REQUIRED(2, "MANUAL_REQUIRED", "转人工审核");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceAutomaticReviewOutcome(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
