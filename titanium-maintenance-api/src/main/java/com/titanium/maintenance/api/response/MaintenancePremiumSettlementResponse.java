package com.titanium.maintenance.api.response;

import java.math.BigDecimal;

/**
 * 保全生命周期费用跨域登记结果。
 *
 * <p>🔴 {@code commissionClawbackAmount} 为**需回拨佣金合计**，口径权威在 billing（佣金调整的生成方），
 * 本域原样透出。该金额仅在**本次结算回执**中返回，读回（查询/重放）路径为 {@code null}——表示
 * 「本路径未携带」，与「追回 0 元」语义不同，调用方不得按 0 处理。</p>
 */
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
        Integer commissionAdjustmentCount,
        BigDecimal commissionClawbackAmount) {
}
