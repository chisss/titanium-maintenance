package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯时点之后可能被保全改变的交易类型。 */
@Getter
public enum MaintenanceRetroactiveImpactType implements BaseEnum {
    SUBSEQUENT_ENDORSEMENT(1, "SUBSEQUENT_ENDORSEMENT", "后续批单"),
    PREMIUM_BILL(2, "PREMIUM_BILL", "保费账单"),
    RENEWAL(3, "RENEWAL", "续期"),
    COLLECTION(4, "COLLECTION", "收款"),
    REFUND(5, "REFUND", "退款"),
    CLAIM(6, "CLAIM", "理赔"),
    BENEFIT(7, "BENEFIT", "给付");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
