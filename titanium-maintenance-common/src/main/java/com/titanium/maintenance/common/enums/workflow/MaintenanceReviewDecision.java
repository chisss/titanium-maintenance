package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全审核结论。 */
@Getter
public enum MaintenanceReviewDecision implements BaseEnum {
    APPROVE(1, "APPROVE", "通过"),
    REJECT(2, "REJECT", "拒绝");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceReviewDecision(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
