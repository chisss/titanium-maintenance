package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Product 返回的单个追溯期间价格差异。 */
public record MaintenanceRetroactiveProductPeriodDifference(
        String periodId,
        String sourceReferenceId,
        LocalDateTime periodStart,
        BigDecimal originalAmount,
        BigDecimal recalculatedAmount,
        MaintenanceBalanceDirection direction,
        BigDecimal differenceAmount,
        String currency,
        String sourceEvidenceHash,
        String resultHash) {

    public MaintenanceRetroactiveProductPeriodDifference {
        periodId = text(periodId, "periodId");
        sourceReferenceId = text(sourceReferenceId, "sourceReferenceId");
        currency = text(currency, "currency").toUpperCase(Locale.ROOT);
        sourceEvidenceHash = hash(sourceEvidenceHash, "sourceEvidenceHash");
        resultHash = hash(resultHash, "resultHash");
        if (periodStart == null || originalAmount == null || originalAmount.signum() < 0
                || recalculatedAmount == null || recalculatedAmount.signum() < 0 || direction == null
                || differenceAmount == null || differenceAmount.signum() < 0) {
            throw invalid("期间价格差异不完整或金额非法");
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
        return new MaintenanceValidationException("MaintenanceRetroactiveProductPeriodDifference", "period", message);
    }
}
