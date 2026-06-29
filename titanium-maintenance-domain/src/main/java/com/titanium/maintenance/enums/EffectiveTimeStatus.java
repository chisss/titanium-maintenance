package com.titanium.maintenance.enums;

import lombok.Getter;

/**
 * 保全生效时间状态枚举
 * <p>
 * 表示保全变更生效时点记录（maintenance_effective_time）的处理状态，为保全域专用分类。
 * 取值来源于建表脚本注释：PENDING(待生效) / EFFECTIVE(已生效) / EXPIRED(已过期)。
 */
@Getter
public enum EffectiveTimeStatus {
    PENDING(1, "PENDING", "待生效", "生效时点尚未到达，变更未生效"),
    EFFECTIVE(2, "EFFECTIVE", "已生效", "生效时点已到达，变更已生效"),
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
        for (EffectiveTimeStatus status : EffectiveTimeStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
