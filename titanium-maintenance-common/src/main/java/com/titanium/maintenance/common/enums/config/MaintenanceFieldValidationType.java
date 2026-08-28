package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全字段值格式校验类型。 */
@Getter
public enum MaintenanceFieldValidationType implements BaseEnum {
    NONE(1, "NONE", "不校验"),
    EMAIL(2, "EMAIL", "邮箱"),
    MOBILE_CN(3, "MOBILE_CN", "中国大陆手机号"),
    GENDER(4, "GENDER", "性别"),
    ID_CARD_CN(5, "ID_CARD_CN", "中国大陆身份证号"),
    POSTAL_CODE_CN(6, "POSTAL_CODE_CN", "中国大陆邮政编码"),
    CUSTOM_REGEX(7, "CUSTOM_REGEX", "自定义正则");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceFieldValidationType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenanceFieldValidationType fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceFieldValidationType.class, code);
    }
}
