package com.titanium.maintenance.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保全费用差额跨域登记状态。
 *
 * <p>该状态同时描述 Product 差额、Billing 余额登记及异步资金结算检查点；其中 POSTED 仍不表示已经收款或退款。</p>
 */
@Getter
public enum MaintenancePremiumSettlementStatus implements BaseEnum {
    NOT_STARTED(1, "NOT_STARTED", "未开始"),
    ADJUSTMENT_CONFIRMED(2, "ADJUSTMENT_CONFIRMED", "差额已确认"),
    POSTED(3, "POSTED", "余额事实已登记"),
    NOT_REQUIRED(4, "NOT_REQUIRED", "无客户余额影响"),
    SETTLEMENT_PENDING(5, "SETTLEMENT_PENDING", "资金结算处理中"),
    SETTLEMENT_FAILED(6, "SETTLEMENT_FAILED", "资金结算失败"),
    SETTLED(7, "SETTLED", "资金结算完成");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenancePremiumSettlementStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    public static MaintenancePremiumSettlementStatus fromCode(String code) {
        return BaseEnum.fromCode(MaintenancePremiumSettlementStatus.class, code);
    }
}
