package com.titanium.maintenance.valueobject.withdrawal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Billing 对原生命周期入账执行冲正后的不可变权威证据。 */
public record MaintenanceBillingReversalEvidence(
        String reversalId,
        String requestId,
        String requestHash,
        String resultHash,
        String sourcePostingId,
        String sourceResultHash,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime reversedAt) {

    public MaintenanceBillingReversalEvidence {
        if (!hasText(reversalId) || !hasText(requestId) || !hash(requestHash) || !hash(resultHash)
                || !hasText(sourcePostingId) || !hash(sourceResultHash) || direction == null
                || amount == null || amount.signum() <= 0 || !hasText(currency)
                || !"REVERSED".equals(status) || reversedAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceBillingReversalEvidence", "Billing 冲正证据字段不完整或无效");
        }
        currency = currency.trim().toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }
}
