package com.titanium.maintenance.application.model.settlement;
import java.math.BigDecimal;

import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementResult;

/** 退保价值证据及其费用/资金结算结果。 */
public record MaintenanceSurrenderSettlementResult(
        MaintenancePremiumSettlementResult settlement,
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
