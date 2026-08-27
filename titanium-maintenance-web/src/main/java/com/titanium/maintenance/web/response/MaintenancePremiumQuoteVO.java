package com.titanium.maintenance.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;

/** 费用任务当前 Product 报价检查点。 */
public record MaintenancePremiumQuoteVO(
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
