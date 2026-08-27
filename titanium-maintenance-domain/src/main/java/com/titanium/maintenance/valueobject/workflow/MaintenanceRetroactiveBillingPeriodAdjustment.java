package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Billing 返回的单个追溯期间处理结果。 */
public record MaintenanceRetroactiveBillingPeriodAdjustment(
        String periodId,
        String sourceReferenceId,
        String accountingPeriod,
        LocalDateTime periodStart,
        BigDecimal originalAmount,
        BigDecimal recalculatedAmount,
        MaintenanceBalanceDirection direction,
        BigDecimal differenceAmount,
        String currency,
        String status,
        String sourceEvidenceHash,
        String productResultHash,
        String billingResultHash) {

    public MaintenanceRetroactiveBillingPeriodAdjustment {
        periodId = text(periodId, "periodId");
        sourceReferenceId = text(sourceReferenceId, "sourceReferenceId");
        accountingPeriod = text(accountingPeriod, "accountingPeriod");
        currency = text(currency, "currency").toUpperCase(Locale.ROOT);
        status = text(status, "status");
        sourceEvidenceHash = hash(sourceEvidenceHash, "sourceEvidenceHash");
        productResultHash = hash(productResultHash, "productResultHash");
        billingResultHash = hash(billingResultHash, "billingResultHash");
        if (periodStart == null || originalAmount == null || originalAmount.signum() < 0
                || recalculatedAmount == null || recalculatedAmount.signum() < 0 || direction == null
                || differenceAmount == null || differenceAmount.signum() < 0) {
            throw invalid("Billing期间调整结果不完整或金额非法");
        }
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + "不能为空");
        }
        return value.trim();
    }

    private static String hash(String value, String field) {
        String result = text(value, field).toLowerCase(Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) {
            throw invalid(field + "必须为SHA-256");
        }
        return result;
    }

    private static MaintenanceValidationException invalid(String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactiveBillingPeriodAdjustment", "period", message);
    }
}
