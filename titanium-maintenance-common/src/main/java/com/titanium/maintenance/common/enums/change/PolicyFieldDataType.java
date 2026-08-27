package com.titanium.maintenance.common.enums.change;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** Policy 字段目录对外发布的数据类型。 */
@Getter
public enum PolicyFieldDataType implements BaseEnum {
    TEXT(1, "TEXT", "文本"),
    INTEGER(2, "INTEGER", "整数"),
    DECIMAL(3, "DECIMAL", "小数"),
    BOOLEAN(4, "BOOLEAN", "布尔值"),
    DATE(5, "DATE", "日期"),
    DATETIME(6, "DATETIME", "日期时间"),
    ENUM(7, "ENUM", "枚举"),
    OBJECT(8, "OBJECT", "对象"),
    ARRAY(9, "ARRAY", "数组");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PolicyFieldDataType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
