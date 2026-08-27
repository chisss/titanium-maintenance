package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯期间重算状态，与 Policy 生效状态正交。 */
@Getter
public enum MaintenanceRetroactivePeriodRecalculationStatus implements BaseEnum {
    RECALCULATING(1, "RECALCULATING", "重算中"),
    PRODUCT_COMPLETED(2, "PRODUCT_COMPLETED", "Product重算完成"),
    COMPLETED(3, "COMPLETED", "重算完成"),
    REVIEW_REQUIRED(4, "REVIEW_REQUIRED", "需人工复核"),
    FAILED(5, "FAILED", "重算失败");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactivePeriodRecalculationStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenanceRetroactivePeriodRecalculationStatus fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceRetroactivePeriodRecalculationStatus.class, code);
    }
}
