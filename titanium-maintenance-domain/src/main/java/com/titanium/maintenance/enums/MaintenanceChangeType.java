package com.titanium.maintenance.enums;

import lombok.Getter;

/**
 * 保全变更项类型枚举
 * <p>
 * 表示保全案件下单条字段变更记录的操作类型，为保全域专用分类。
 */
@Getter
public enum MaintenanceChangeType {
    ADD(1, "ADD", "新增", "新增字段值"),
    MODIFY(2, "MODIFY", "修改", "修改字段值"),
    DELETE(3, "DELETE", "删除", "删除字段值");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    MaintenanceChangeType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static MaintenanceChangeType fromCode(String code) {
        for (MaintenanceChangeType type : MaintenanceChangeType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
