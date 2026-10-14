package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/**
 * 保全生效时间状态枚举
 * <p>
 * 表示保全变更生效时点记录（maintenance_effective_time）的处理状态，为保全域专用分类。
 * 取值来源于建表脚本注释：PENDING(待生效) / EFFECTIVE(已生效) / EXPIRED(已过期)。
 */
@Getter
public enum EffectiveTimeStatus implements BaseEnum {
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PENDING(1, "PENDING", "待生效", "生效时点尚未到达，变更未生效"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    EFFECTIVE(2, "EFFECTIVE", "已生效", "生效时点已到达，变更已生效"),
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    EXPIRED(3, "EXPIRED", "已过期", "生效时点已过期，变更未能生效");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    EffectiveTimeStatus(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static EffectiveTimeStatus fromCode(String code) {
        return BaseEnum.fromCode(EffectiveTimeStatus.class, code);
    }
}
