package com.titanium.maintenance.api.response;

import java.math.BigDecimal;

/** 退保价值证据及资金结算结果。 */
public record MaintenanceSurrenderSettlementResponse(
        MaintenancePremiumSettlementResponse settlement,
        String policyCode,
        String policyVersion,
        String policyContentHash,
        Integer policyYear,
        Integer coolingOffDays,
        String refundType,
        Boolean withinCoolingOff,
        BigDecimal cashValueRate,
        BigDecimal retainedCustomerAmount,
        BigDecimal internalCostRetentionRate) {
}
