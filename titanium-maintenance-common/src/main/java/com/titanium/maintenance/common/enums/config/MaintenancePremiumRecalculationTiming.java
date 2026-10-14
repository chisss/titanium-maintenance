package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/** 保全费用重算时点。 */
@Getter
public enum MaintenancePremiumRecalculationTiming implements BaseEnum {
    NOT_APPLICABLE(1, "NOT_APPLICABLE", "不适用"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    AFTER_DATA_ENTRY(2, "AFTER_DATA_ENTRY", "信息录入后"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    BEFORE_SETTLEMENT(3, "BEFORE_SETTLEMENT", "收退费前"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    BEFORE_EFFECTIVE(4, "BEFORE_EFFECTIVE", "生效前");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenancePremiumRecalculationTiming(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
