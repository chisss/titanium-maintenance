package com.titanium.maintenance.common.enums;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/** 保全客户余额差额方向。 */
@Getter
public enum MaintenanceBalanceDirection implements BaseEnum {
    DEBIT(1, "DEBIT", "追加应收"),
    CREDIT(2, "CREDIT", "客户贷方余额"),
    NONE(3, "NONE", "无余额影响");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceBalanceDirection(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenanceBalanceDirection fromCode(String code) {
        if (code == null) {
            throw new MaintenanceValidationException("MaintenanceBalanceDirection", "code", "余额方向不能为空");
        }
        MaintenanceBalanceDirection direction = BaseEnum.fromCode(MaintenanceBalanceDirection.class, code);
        if (direction == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceBalanceDirection", "code", "不支持的余额方向: " + code);
        }
        return direction;
    }
}
