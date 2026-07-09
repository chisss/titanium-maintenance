package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 退费类型枚举（退保场景）
 * <p>
 * 表示退保时退费金额的计算方式，为保全域专用分类：
 * <ul>
 *   <li>{@code FULL_REFUND}：犹豫期内退保，全额退还已缴保费；</li>
 *   <li>{@code DEDUCT_CASH_VALUE}：犹豫期外退保，仅退还保单现金价值（已扣除风险保费与费用）。</li>
 * </ul>
 * </p>
 */
@Getter
public enum RefundType implements BaseEnum {
    FULL_REFUND(1, "FULL_REFUND", "全额退费", "犹豫期内退保，全额退还已缴保费"),
    DEDUCT_CASH_VALUE(2, "DEDUCT_CASH_VALUE", "扣除现金价值", "犹豫期外退保，退还保单现金价值");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    RefundType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public static RefundType fromCode(String code) {
        return BaseEnum.fromCode(RefundType.class, code);
    }
}
