package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 归一化后的 Payment 收退款资金状态。 */
@Getter
public enum MaintenanceFundSettlementStatus implements BaseEnum {
    NOT_REQUIRED(1, "NOT_REQUIRED", "无需资金处理"),
    PENDING(2, "PENDING", "待处理"),
    PROCESSING(3, "PROCESSING", "处理中"),
    SUCCEEDED(4, "SUCCEEDED", "已成功"),
    FAILED(5, "FAILED", "处理失败"),
    REVERSED(6, "REVERSED", "已冲销");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceFundSettlementStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public boolean completed() {
        return this == NOT_REQUIRED || this == SUCCEEDED;
    }

    public boolean failed() {
        return this == FAILED || this == REVERSED;
    }
}
