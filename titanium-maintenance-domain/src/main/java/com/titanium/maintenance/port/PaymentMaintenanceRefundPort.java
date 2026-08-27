package com.titanium.maintenance.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Maintenance 调用 Payment 独立退款单的出口端口。 */
public interface PaymentMaintenanceRefundPort {

    RefundFact create(RefundRequest request);

    RefundFact get(String tenantId, String refundOrderId);

    record RefundRequest(
            String tenantId,
            String refundRequestId,
            String sourcePostingId,
            String originalPaymentId,
            BigDecimal amount,
            String currency,
            String reason,
            String requestedBy) {
    }

    record RefundFact(
            String refundOrderId,
            String refundRequestId,
            String sourcePostingId,
            String originalPaymentId,
            BigDecimal amount,
            String currency,
            String status,
            String failureCode,
            String failureMessage,
            LocalDateTime updatedAt) {
    }
}
