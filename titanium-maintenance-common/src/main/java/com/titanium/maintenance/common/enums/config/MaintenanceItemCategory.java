package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/** 保全项业务分类。 */
@Getter
public enum MaintenanceItemCategory implements BaseEnum {
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    BASIC_INFORMATION(1, "BASIC_INFORMATION", "基本资料"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    CONTRACT_PARTY(2, "CONTRACT_PARTY", "合同参与方"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    COVERAGE(3, "COVERAGE", "保障责任"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PAYMENT_AND_BENEFIT(4, "PAYMENT_AND_BENEFIT", "缴费与领取"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    CONTRACT_STATUS(5, "CONTRACT_STATUS", "合同状态"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    FUND_ACCOUNT(6, "FUND_ACCOUNT", "资金账户"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    TERMINATION(7, "TERMINATION", "合同终止"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
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
