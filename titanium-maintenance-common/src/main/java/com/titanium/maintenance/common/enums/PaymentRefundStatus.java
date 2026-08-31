package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * Payment 域退款状态枚举（下游退款事实状态码的域内镜像）
 * <p>
 * 保全域消费 Payment 退费/退款事实时用于业务判断（结算状态推导）的状态码，值域与 Payment 域退款状态一致。
 * 仅收录保全域实际消费的码值，未收录的码值按未知处理（结算保持 PENDING，与原字符串比较语义一致）。
 * </p>
 */
@Getter
public enum PaymentRefundStatus implements BaseEnum {
    SUCCEEDED(1, "SUCCEEDED", "退款成功"),
    FAILED(2, "FAILED", "退款失败"),
    CANCELLED(3, "CANCELLED", "退款取消"),
    NOT_REQUIRED(4, "NOT_REQUIRED", "无需退款");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PaymentRefundStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /** 按语言无关 code 反查；未知/空值返回 null（调用方按 PENDING 等默认分支处理） */
    public static PaymentRefundStatus fromCode(String code) {
        return BaseEnum.fromCode(PaymentRefundStatus.class, code);
    }
}
