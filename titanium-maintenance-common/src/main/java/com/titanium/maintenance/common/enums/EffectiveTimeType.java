package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保全生效时间类型枚举
 * <p>
 * 表示保全变更的生效时点策略，为保全域专用分类。
 */
@Getter
public enum EffectiveTimeType implements BaseEnum {
    IMMEDIATE(1, "IMMEDIATE", "立即", "保全变更立即生效"),
    NEXT_PERIOD(2, "NEXT_PERIOD", "次期", "保全变更于次期生效"),
    SPECIFIED_DATE(3, "SPECIFIED_DATE", "指定日", "保全变更于指定日期生效"),
    RETROACTIVE(4, "RETROACTIVE", "追溯生效", "保全变更追溯至历史业务时间生效"),
    FUTURE(5, "FUTURE", "未来生效", "保全变更于未来计划时间生效"),
    NEXT_BILLING_DATE(6, "NEXT_BILLING_DATE", "次期缴费日", "保全变更于下一缴费日生效"),
    POLICY_ANNIVERSARY(7, "POLICY_ANNIVERSARY", "保单周年日", "保全变更于保单周年日生效");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    EffectiveTimeType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static EffectiveTimeType fromCode(String code) {
        return BaseEnum.fromCode(EffectiveTimeType.class, code);
    }
}
