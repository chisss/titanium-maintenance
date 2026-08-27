package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Maintenance 保存的 Product 期间重算检查点。 */
public record MaintenanceRetroactiveProductRecalculationEvidence(
        String recalculationId,
        String recalculationVersion,
        String originalCalculationId,
        String originalResultHash,
        String replacementCalculationId,
        String replacementResultHash,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        String inputHash,
        String resultHash,
        LocalDateTime calculatedAt,
        List<MaintenanceRetroactiveProductPeriodDifference> periods) {

    public MaintenanceRetroactiveProductRecalculationEvidence {
        recalculationId = text(recalculationId, "recalculationId");
        recalculationVersion = text(recalculationVersion, "recalculationVersion");
        originalCalculationId = text(originalCalculationId, "originalCalculationId");
        originalResultHash = hash(originalResultHash, "originalResultHash");
        replacementCalculationId = text(replacementCalculationId, "replacementCalculationId");
        replacementResultHash = hash(replacementResultHash, "replacementResultHash");
        currency = text(currency, "currency").toUpperCase(Locale.ROOT);
        inputHash = hash(inputHash, "inputHash");
        resultHash = hash(resultHash, "resultHash");
        periods = periods == null ? List.of() : List.copyOf(periods);
        if (direction == null || amount == null || amount.signum() < 0 || calculatedAt == null
                || new HashSet<>(periods.stream().map(MaintenanceRetroactiveProductPeriodDifference::periodId)
                        .toList()).size() != periods.size()) {
            throw invalid("Product期间重算证据不完整或期间重复");
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
                "MaintenanceRetroactiveProductRecalculationEvidence", "evidence", message);
    }
}
