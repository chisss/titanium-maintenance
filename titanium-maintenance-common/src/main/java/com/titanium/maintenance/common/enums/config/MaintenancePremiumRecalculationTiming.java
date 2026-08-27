package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全费用重算时点。 */
@Getter
public enum MaintenancePremiumRecalculationTiming implements BaseEnum {
    NOT_APPLICABLE(1, "NOT_APPLICABLE", "不适用"),
    AFTER_DATA_ENTRY(2, "AFTER_DATA_ENTRY", "信息录入后"),
    BEFORE_SETTLEMENT(3, "BEFORE_SETTLEMENT", "收退费前"),
    BEFORE_EFFECTIVE(4, "BEFORE_EFFECTIVE", "生效前");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenancePremiumRecalculationTiming(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
