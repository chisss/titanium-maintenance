package com.titanium.maintenance.enums;

import lombok.Getter;

/**
 * 保全生效时间类型枚举
 * <p>
 * 表示保全变更的生效时点策略，为保全域专用分类。
 */
@Getter
public enum EffectiveTimeType {
    IMMEDIATE(1, "IMMEDIATE", "立即", "保全变更立即生效"),
    NEXT_PERIOD(2, "NEXT_PERIOD", "次期", "保全变更于次期生效"),
    SPECIFIED_DATE(3, "SPECIFIED_DATE", "指定日", "保全变更于指定日期生效");

    private final Integer enumCode;
    private final String code;
    private final String name;
    private final String desc;

    EffectiveTimeType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static EffectiveTimeType fromCode(String code) {
        for (EffectiveTimeType type : EffectiveTimeType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
