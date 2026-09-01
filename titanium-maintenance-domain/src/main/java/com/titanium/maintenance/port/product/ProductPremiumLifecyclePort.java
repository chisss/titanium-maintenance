package com.titanium.maintenance.port.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

/** Product 生命周期费用差额端口。 */
public interface ProductPremiumLifecyclePort {

    CalculationFact calculateReplacement(CalculationRequest request);

    AdjustmentFact createAdjustment(AdjustmentRequest request);

    AdjustmentFact createReversal(ReversalRequest request);

    record CalculationRequest(
            String tenantId,
            String productId,
            String calculationRequestId,
            String bizNo,
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
            List<UnderwritingAdjustment> underwritingAdjustments,
            String channelId,
            Integer policyYear) {
    }

    record UnderwritingAdjustment(
            String adjustmentCode,
            String type,
            BigDecimal value,
            String reason,
            String ruleVersion) {
    }

    record CalculationFact(
            String calculationId,
            String calculationRequestId,
            String bizNo,
            String purpose,
            String productId,
            String productVersion,
            String currency,
            String resultHash) {
    }

    record AdjustmentRequest(
            String tenantId,
            String adjustmentRequestId,
            String bizNo,
            String lifecycleType,
            String originalCalculationId,
            String replacementCalculationId,
            LocalDateTime businessTime,
            String reason) {
    }

    record ReversalRequest(
            String tenantId,
            String adjustmentRequestId,
            String sourceAdjustmentId,
            LocalDateTime businessTime,
            String reason) {
    }

    record AdjustmentFact(
            String adjustmentId,
            String adjustmentRequestId,
            String originalCalculationId,
            String replacementCalculationId,
            String resultHash,
            MaintenanceBalanceDirection direction,
            BigDecimal customerAmount,
            String currency) {
    }
}
