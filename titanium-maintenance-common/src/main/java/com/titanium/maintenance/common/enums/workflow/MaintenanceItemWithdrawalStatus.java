package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全项目撤销状态。 */
@Getter
public enum MaintenanceItemWithdrawalStatus implements BaseEnum {
    REQUESTED(1, "REQUESTED", "已申请"),
    REVERSING(2, "REVERSING", "财务冲正中"),
    WAITING_FUNDS(3, "WAITING_FUNDS", "等待资金终态"),
    COMPLETED(4, "COMPLETED", "已撤销"),
    FAILED(5, "FAILED", "撤销失败");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceItemWithdrawalStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
