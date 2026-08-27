package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全费用步骤的报价检查点状态。 */
@Getter
public enum MaintenancePremiumQuoteStatus implements BaseEnum {
    QUOTED(1, "QUOTED", "已报价"),
    NOT_REQUIRED(2, "NOT_REQUIRED", "无需报价");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenancePremiumQuoteStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
