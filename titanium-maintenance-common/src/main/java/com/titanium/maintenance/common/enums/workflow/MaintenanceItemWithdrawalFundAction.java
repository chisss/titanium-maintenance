package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 撤销原费用事实后需要执行的资金动作。 */
@Getter
public enum MaintenanceItemWithdrawalFundAction implements BaseEnum {
    NOT_REQUIRED(1, "NOT_REQUIRED", "无需资金处理"),
    REFUND(2, "REFUND", "退回原收款"),
    COLLECTION(3, "COLLECTION", "追回原退款");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceItemWithdrawalFundAction(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
