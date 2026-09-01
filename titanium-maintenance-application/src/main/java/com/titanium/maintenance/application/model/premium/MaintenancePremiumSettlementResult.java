package com.titanium.maintenance.application.model.premium;

import java.math.BigDecimal;

/** 保全生命周期费用编排结果。 */
public record MaintenancePremiumSettlementResult(
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
