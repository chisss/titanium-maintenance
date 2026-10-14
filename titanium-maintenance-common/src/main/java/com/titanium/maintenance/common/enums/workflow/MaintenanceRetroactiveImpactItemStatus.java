package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/** 追溯影响项的处理状态；M5-05B 新发现项固定为待处理。 */
@Getter
public enum MaintenanceRetroactiveImpactItemStatus implements BaseEnum {
    PENDING(1, "PENDING", "待处理"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    RESOLVED(2, "RESOLVED", "已解决"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    WAIVED(3, "WAIVED", "已豁免");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceRetroactiveImpactItemStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
