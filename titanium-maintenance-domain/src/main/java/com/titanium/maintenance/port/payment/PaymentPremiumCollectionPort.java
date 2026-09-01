package com.titanium.maintenance.port.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Maintenance 调用 Payment 创建或回查追加保费收款单的出口端口。 */
public interface PaymentPremiumCollectionPort {

    CollectionFact create(CollectionRequest request);

    CollectionFact get(String tenantId, String paymentOrderId);

    record CollectionRequest(
            String tenantId,
            String paymentOrderId,
            String policyId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String description) {
    }

    record CollectionFact(
            String paymentOrderId,
            String policyId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String status,
            String transactionId,
            LocalDateTime paymentDate) {
    }
}
