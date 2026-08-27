package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** Billing 对保全费用差额的入账状态。 */
@Getter
public enum MaintenanceBillingPostingStatus implements BaseEnum {
    POSTED(1, "POSTED", "已入账"),
    REVERSED(2, "REVERSED", "已冲正");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceBillingPostingStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenanceBillingPostingStatus fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceBillingPostingStatus.class, code);
    }
}
