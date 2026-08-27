package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 追溯影响事实的权威归属域。 */
@Getter
public enum MaintenanceRetroactiveImpactDomain implements BaseEnum {
    POLICY(1, "POLICY", "保单"),
    BILLING(2, "BILLING", "账务"),
    PAYMENT(3, "PAYMENT", "支付"),
    CLAIM(4, "CLAIM", "理赔");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactDomain(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
