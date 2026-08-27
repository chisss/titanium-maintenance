package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 自动审核必须逐项通过的固定门禁。 */
@Getter
public enum MaintenanceReviewGate implements BaseEnum {
    CHANNEL(1, "CHANNEL", "渠道"),
    PRODUCT(2, "PRODUCT", "产品"),
    ITEM(3, "ITEM", "保全项"),
    IDENTITY(4, "IDENTITY", "身份"),
    MATERIAL(5, "MATERIAL", "材料"),
    AMOUNT(6, "AMOUNT", "金额"),
    RISK(7, "RISK", "风险");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceReviewGate(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
