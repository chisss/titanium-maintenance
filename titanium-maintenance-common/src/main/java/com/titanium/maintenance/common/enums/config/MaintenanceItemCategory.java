package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全项业务分类。 */
@Getter
public enum MaintenanceItemCategory implements BaseEnum {
    BASIC_INFORMATION(1, "BASIC_INFORMATION", "基本资料"),
    CONTRACT_PARTY(2, "CONTRACT_PARTY", "合同参与方"),
    COVERAGE(3, "COVERAGE", "保障责任"),
    PAYMENT_AND_BENEFIT(4, "PAYMENT_AND_BENEFIT", "缴费与领取"),
    CONTRACT_STATUS(5, "CONTRACT_STATUS", "合同状态"),
    FUND_ACCOUNT(6, "FUND_ACCOUNT", "资金账户"),
    TERMINATION(7, "TERMINATION", "合同终止"),
    SERVICE(8, "SERVICE", "服务事项");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceItemCategory(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
