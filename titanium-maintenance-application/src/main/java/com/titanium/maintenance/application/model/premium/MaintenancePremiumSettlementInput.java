package com.titanium.maintenance.application.model.premium;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 发起保全替代计算和余额登记的应用输入。 */
public record MaintenancePremiumSettlementInput(
        String originalCalculationId,
        String productId,
        String productVersion,
        LocalDateTime businessTime,
        String currency,
        BigDecimal sumInsured,
        Integer age,
        String gender,
        Integer paymentTermYears,
        Integer coverageTermYears,
        Integer paymentPeriods,
        Map<String, Object> requestSnapshot,
        List<UnderwritingAdjustmentInput> underwritingAdjustments,
        String channelId,
        Integer policyYear,
        String reason,
        String updatedBy) {

    public record UnderwritingAdjustmentInput(
            String adjustmentCode,
            String type,
            BigDecimal value,
            String reason,
            String ruleVersion) {
    }
}
