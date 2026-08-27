package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.util.Locale;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Maintenance 保存的单个关闭期间结转结果。 */
public record MaintenanceRetroactivePeriodResolutionLine(
        String periodId,
        String sourceAccountingPeriod,
        String targetAccountingPeriod,
        MaintenanceBalanceDirection direction,
        BigDecimal differenceAmount,
        String currency,
        String postingReference,
        String sourceLineResultHash,
        String resultHash) {

    public MaintenanceRetroactivePeriodResolutionLine {
        periodId = text(periodId, "periodId");
        sourceAccountingPeriod = period(sourceAccountingPeriod, "sourceAccountingPeriod");
        targetAccountingPeriod = period(targetAccountingPeriod, "targetAccountingPeriod");
        currency = text(currency, "currency").toUpperCase(Locale.ROOT);
        postingReference = text(postingReference, "postingReference");
        sourceLineResultHash = hash(sourceLineResultHash, "sourceLineResultHash");
        resultHash = hash(resultHash, "resultHash");
        if (direction == null || direction == MaintenanceBalanceDirection.NONE
                || differenceAmount == null || differenceAmount.signum() <= 0) {
            throw invalid("关闭期间结转明细不完整");
        }
    }

    private static String period(String value, String field) {
        String result = text(value, field);
        if (!result.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw invalid(field + "必须为yyyy-MM");
        }
        return result;
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
                "MaintenanceRetroactivePeriodResolutionLine", "line", message);
    }
}
