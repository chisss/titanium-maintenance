package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 解析 Product 保全 Offering 失败的明确原因。 */
@Getter
public enum ProductMaintenanceOfferingFailureReason implements BaseEnum {
    NOT_FOUND(1, "NOT_FOUND", "Offering不存在"),
    VERSION_MISMATCH(2, "VERSION_MISMATCH", "产品或计划版本不匹配"),
    NOT_APPLICABLE(3, "NOT_APPLICABLE", "Offering不适用于当前案件"),
    CONTRACT_INVALID(4, "CONTRACT_INVALID", "Offering契约无效"),
    UNAVAILABLE(5, "UNAVAILABLE", "Offering服务不可用");

    private final Integer enumCode;
    private final String code;
    private final String name;

    ProductMaintenanceOfferingFailureReason(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
