package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Billing 返回的关闭期间处理权威结论。 */
public record MaintenanceRetroactivePeriodResolutionEvidence(
        String billingResolutionId,
        String resolutionRequestId,
        String billingBatchId,
        String sourceBatchResultHash,
        String targetAccountingPeriod,
        int resolvedLineCount,
        String requestHash,
        String resultHash,
        String reason,
        String resolvedBy,
        LocalDateTime resolvedAt,
        List<MaintenanceRetroactivePeriodResolutionLine> lines) {

    public MaintenanceRetroactivePeriodResolutionEvidence {
        billingResolutionId = text(billingResolutionId, "billingResolutionId");
        resolutionRequestId = text(resolutionRequestId, "resolutionRequestId");
        billingBatchId = text(billingBatchId, "billingBatchId");
        sourceBatchResultHash = hash(sourceBatchResultHash, "sourceBatchResultHash");
        String normalizedTargetAccountingPeriod = period(targetAccountingPeriod);
        targetAccountingPeriod = normalizedTargetAccountingPeriod;
        requestHash = hash(requestHash, "requestHash");
        resultHash = hash(resultHash, "resultHash");
        reason = text(reason, "reason");
        resolvedBy = text(resolvedBy, "resolvedBy");
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (resolvedAt == null || resolvedLineCount < 1 || resolvedLineCount != lines.size()
                || lines.stream().anyMatch(
                        line -> !normalizedTargetAccountingPeriod.equals(line.targetAccountingPeriod()))
                || new HashSet<>(lines.stream().map(MaintenanceRetroactivePeriodResolutionLine::periodId)
                        .toList()).size() != lines.size()) {
            throw invalid("关闭期间处理结论不完整");
        }
    }

    private static String period(String value) {
        String result = text(value, "targetAccountingPeriod");
        if (!result.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw invalid("目标会计期间必须为yyyy-MM");
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
                "MaintenanceRetroactivePeriodResolutionEvidence", "evidence", message);
    }
}
