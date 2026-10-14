package com.titanium.maintenance.common.enums.workflow;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/** 保全审核结论。 */
@Getter
public enum MaintenanceReviewDecision implements BaseEnum {
    APPROVE(1, "APPROVE", "通过"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    REJECT(2, "REJECT", "拒绝");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    MaintenanceReviewDecision(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
