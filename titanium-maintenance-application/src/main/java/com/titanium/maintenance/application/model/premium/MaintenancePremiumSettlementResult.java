package com.titanium.maintenance.application.model.premium;

import java.math.BigDecimal;

/**
 * 保全生命周期费用编排结果。
 *
 * <p>🔴 {@code commissionClawbackAmount} 为**需回拨佣金合计**，由 billing（佣金调整的生成方）计算后
 * **原样搬运**，本域不按类型二次求和（R-11 不新建第二套口径）。</p>
 *
 * <p>🔴 该金额**只在本次结算回执中携带**（不落库、不进读模型），故 {@code get}/{@code 重放} 等读回
 * 路径返回 {@code null}——{@code null} 表示「本路径未携带该事实」，与「追回 0 元」是两种语义，
 * 调用方不得把 {@code null} 当 0 处理。历史口径请查 billing 的佣金调整流水（权威侧）。</p>
 */
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
        Integer commissionAdjustmentCount,
        BigDecimal commissionClawbackAmount) {
}
