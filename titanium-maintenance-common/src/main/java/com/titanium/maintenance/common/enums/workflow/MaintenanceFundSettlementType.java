package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全费用对应的资金处理类型。 */
@Getter
public enum MaintenanceFundSettlementType implements BaseEnum {
    NOT_REQUIRED(1, "NOT_REQUIRED", "无需资金处理"),
    COLLECTION(2, "COLLECTION", "收款"),
    REFUND(3, "REFUND", "退款");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceFundSettlementType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
