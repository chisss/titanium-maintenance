package com.titanium.maintenance.application.model.premium;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;

/** 费用任务当前报价检查点结果。 */
public record MaintenancePremiumQuoteResult(
        MaintenancePremiumQuoteStatus status,
        String quoteId,
        String quoteVersion,
        String originalCalculationId,
        String replacementCalculationId,
        String pricingPlanVersion,
        String resultHash,
        String detailSummary,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        LocalDateTime quotedAt,
        LocalDateTime validUntil) {
}
