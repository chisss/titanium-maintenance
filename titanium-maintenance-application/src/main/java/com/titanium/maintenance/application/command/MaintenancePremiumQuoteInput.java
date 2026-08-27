package com.titanium.maintenance.application.command;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 触发费用任务 Product 报价的应用层输入。 */
public record MaintenancePremiumQuoteInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String lifecycleType,
        String originalCalculationId,
        String currency,
        BigDecimal sumInsured,
        Integer age,
        String gender,
        Integer paymentTermYears,
        Integer coverageTermYears,
        Integer paymentPeriods,
        Map<String, Object> pricingFactors,
        List<UnderwritingAdjustmentInput> underwritingAdjustments,
        String channelId,
        Integer policyYear,
        String reason,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {

    public MaintenancePremiumQuoteInput {
        pricingFactors = pricingFactors == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(pricingFactors));
        underwritingAdjustments = underwritingAdjustments == null
                ? List.of()
                : List.copyOf(underwritingAdjustments);
    }

    /** Product 核保调费输入，不允许携带最终金额。 */
    public record UnderwritingAdjustmentInput(
            String adjustmentCode,
            String type,
            BigDecimal value,
            String reason,
            String ruleVersion) {
    }
}
