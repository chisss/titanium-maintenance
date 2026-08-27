package com.titanium.maintenance.api.response;

import java.math.BigDecimal;

/** 保全生命周期费用跨域登记结果。 */
public record MaintenancePremiumSettlementResponse(
        String maintenanceId,
        String premiumSettlementStatus,
        String originalCalculationId,
        String replacementCalculationId,
        String adjustmentId,
        String adjustmentResultHash,
        String billingPostingId,
        String billingPostingStatus,
        String direction,
        BigDecimal amount,
        String currency,
        String refundInstructionId,
        String refundOrderId,
        String refundStatus,
        Integer commissionAdjustmentCount) {
}
