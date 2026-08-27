package com.titanium.maintenance.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

/** Product 退保价值确认端口。 */
public interface ProductSurrenderValuePort {

    SurrenderFact calculate(SurrenderRequest request);

    record SurrenderRequest(
            String tenantId,
            String surrenderRequestId,
            String bizNo,
            String originalBizNo,
            String originalCalculationId,
            LocalDate policyEffectiveDate,
            LocalDate surrenderDate,
            Integer policyYear,
            LocalDateTime businessTime,
            String reason) {
    }

    record SurrenderFact(
            String surrenderRequestId,
            String policyCode,
            String policyVersion,
            String policyContentHash,
            Integer policyYear,
            Integer coolingOffDays,
            String refundType,
            Boolean withinCoolingOff,
            BigDecimal cashValueRate,
            BigDecimal refundAmount,
            BigDecimal retainedCustomerAmount,
            BigDecimal internalCostRetentionRate,
            String originalCalculationId,
            String originalResultHash,
            String replacementCalculationId,
            String replacementResultHash,
            String adjustmentId,
            String requestHash,
            String adjustmentResultHash,
            String pricingPlanVersion,
            String pricingPlanContentHash,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency) {

        /** 兼容旧测试与历史适配器；新契约由 Product 返回真实原/替代结果摘要。 */
        public SurrenderFact(
                String surrenderRequestId,
                String policyCode,
                String policyVersion,
                String policyContentHash,
                Integer policyYear,
                Integer coolingOffDays,
                String refundType,
                Boolean withinCoolingOff,
                BigDecimal cashValueRate,
                BigDecimal refundAmount,
                BigDecimal retainedCustomerAmount,
                BigDecimal internalCostRetentionRate,
                String originalCalculationId,
                String replacementCalculationId,
                String adjustmentId,
                String adjustmentResultHash,
                MaintenanceBalanceDirection direction,
                BigDecimal amount,
                String currency) {
            this(surrenderRequestId, policyCode, policyVersion, policyContentHash, policyYear,
                    coolingOffDays, refundType, withinCoolingOff, cashValueRate, refundAmount,
                    retainedCustomerAmount, internalCostRetentionRate, originalCalculationId,
                    adjustmentResultHash, replacementCalculationId, adjustmentResultHash,
                    adjustmentId, adjustmentResultHash, adjustmentResultHash,
                    policyVersion, policyContentHash, direction, amount, currency);
        }
    }
}
