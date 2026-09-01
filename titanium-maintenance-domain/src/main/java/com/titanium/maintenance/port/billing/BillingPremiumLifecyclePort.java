package com.titanium.maintenance.port.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

/** Billing 生命周期余额事实端口。 */
public interface BillingPremiumLifecyclePort {

    PostingFact post(PostingRequest request);

    ReversalFact reverse(ReversalRequest request);

    record PostingRequest(
            String tenantId,
            String adjustmentId,
            String adjustmentResultHash,
            String policyId,
            String customerId,
            String createdBy) {
    }

    record PostingFact(
            String postingId,
            String adjustmentId,
            String resultHash,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            String status,
            String refundInstructionId,
            String refundOrderId,
            String refundStatus,
            Integer commissionAdjustmentCount) {
    }

    record ReversalRequest(
            String tenantId,
            String sourcePostingId,
            String requestId,
            String reason,
            String createdBy) {
    }

    record ReversalFact(
            String reversalId,
            String requestId,
            String requestHash,
            String resultHash,
            String sourcePostingId,
            String sourceResultHash,
            String policyId,
            String customerId,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime createdAt) {
    }
}
