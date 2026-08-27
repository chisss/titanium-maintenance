package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Maintenance 保存的 Billing 期间调整检查点。 */
public record MaintenanceRetroactiveBillingAdjustmentEvidence(
        String batchId,
        String status,
        int postedCount,
        int reviewCount,
        String requestHash,
        String resultHash,
        LocalDateTime adjustedAt,
        List<MaintenanceRetroactiveBillingPeriodAdjustment> periods) {

    public MaintenanceRetroactiveBillingAdjustmentEvidence {
        batchId = text(batchId, "batchId");
        status = text(status, "status");
        requestHash = hash(requestHash, "requestHash");
        resultHash = hash(resultHash, "resultHash");
        periods = periods == null ? List.of() : List.copyOf(periods);
        if (postedCount < 0 || reviewCount < 0 || adjustedAt == null
                || postedCount + reviewCount > periods.size()
                || new HashSet<>(periods.stream().map(MaintenanceRetroactiveBillingPeriodAdjustment::periodId)
                        .toList()).size() != periods.size()) {
            throw invalid("Billing期间调整证据计数非法或期间重复");
        }
    }

    public boolean requiresReview() {
        return reviewCount > 0 || "REVIEW_REQUIRED".equals(status);
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
                "MaintenanceRetroactiveBillingAdjustmentEvidence", "evidence", message);
    }
}
