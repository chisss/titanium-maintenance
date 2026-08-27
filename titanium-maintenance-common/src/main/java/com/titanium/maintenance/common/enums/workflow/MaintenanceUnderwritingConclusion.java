package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** Maintenance 侧冻结的保全核保结论。 */
@Getter
public enum MaintenanceUnderwritingConclusion implements BaseEnum {
    NOT_REQUIRED(1, "NOT_REQUIRED", "无需核保"),
    APPROVED(2, "APPROVED", "核保通过"),
    CONDITIONAL_APPROVED(3, "CONDITIONAL_APPROVED", "附加条件通过"),
    MANUAL_REVIEW(4, "MANUAL_REVIEW", "人工核保"),
    REJECTED(5, "REJECTED", "核保拒绝");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceUnderwritingConclusion(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public boolean completed() {
        return this != MANUAL_REVIEW;
    }

    public boolean accepted() {
        return this == APPROVED || this == CONDITIONAL_APPROVED || this == NOT_REQUIRED;
    }

    public static MaintenanceUnderwritingConclusion fromCode(String code) {
        MaintenanceUnderwritingConclusion conclusion = BaseEnum.fromCode(
                MaintenanceUnderwritingConclusion.class, code);
        if (conclusion == null) {
            throw new IllegalArgumentException("无效的保全核保结论: " + code);
        }
        return conclusion;
    }
}
