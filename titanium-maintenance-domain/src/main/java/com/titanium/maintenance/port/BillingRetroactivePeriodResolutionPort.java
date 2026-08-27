package com.titanium.maintenance.port;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Billing 关闭会计期间处理正式端口。 */
public interface BillingRetroactivePeriodResolutionPort {

    ResolutionFact resolve(ResolutionRequest request);

    ResolutionFact get(String tenantId, String billingBatchId);

    record ResolutionRequest(
            String tenantId,
            String maintenanceId,
            String policyId,
            String billingBatchId,
            String sourceBatchResultHash,
            String resolutionRequestId,
            YearMonth targetAccountingPeriod,
            String reason,
            String operatorId) {

        public ResolutionRequest {
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            billingBatchId = text("billingBatchId", billingBatchId);
            sourceBatchResultHash = hash("sourceBatchResultHash", sourceBatchResultHash);
            resolutionRequestId = text("resolutionRequestId", resolutionRequestId);
            reason = text("reason", reason);
            operatorId = text("operatorId", operatorId);
            if (targetAccountingPeriod == null) {
                throw invalid("targetAccountingPeriod", "目标会计期间不能为空");
            }
        }

        public String payloadHash() {
            return sha256(String.join("|", tenantId, maintenanceId, policyId, billingBatchId,
                    sourceBatchResultHash, resolutionRequestId, targetAccountingPeriod.toString(),
                    reason, operatorId));
        }
    }

    record ResolutionFact(
            String billingResolutionId,
            String resolutionRequestId,
            String billingBatchId,
            String tenantId,
            String maintenanceId,
            String policyId,
            String sourceBatchResultHash,
            YearMonth targetAccountingPeriod,
            String status,
            int resolvedLineCount,
            String requestHash,
            String resultHash,
            String reason,
            String resolvedBy,
            LocalDateTime resolvedAt,
            List<ResolutionLineFact> lines) {

        public ResolutionFact {
            billingResolutionId = text("billingResolutionId", billingResolutionId);
            resolutionRequestId = text("resolutionRequestId", resolutionRequestId);
            billingBatchId = text("billingBatchId", billingBatchId);
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            sourceBatchResultHash = hash("sourceBatchResultHash", sourceBatchResultHash);
            status = text("status", status);
            requestHash = hash("requestHash", requestHash);
            resultHash = hash("resultHash", resultHash);
            reason = text("reason", reason);
            resolvedBy = text("resolvedBy", resolvedBy);
            lines = lines == null ? List.of() : List.copyOf(lines);
            if (targetAccountingPeriod == null || resolvedAt == null || resolvedLineCount < 1
                    || resolvedLineCount != lines.size()
                    || lines.stream().anyMatch(
                            line -> !targetAccountingPeriod.equals(line.targetAccountingPeriod()))
                    || new HashSet<>(lines.stream().map(ResolutionLineFact::periodId).toList()).size()
                            != lines.size()) {
                throw invalid("fact", "Billing关闭期间处理事实不完整");
            }
        }
    }

    record ResolutionLineFact(
            String periodId,
            YearMonth sourceAccountingPeriod,
            YearMonth targetAccountingPeriod,
            MaintenanceBalanceDirection direction,
            BigDecimal differenceAmount,
            String currency,
            String postingReference,
            String sourceLineResultHash,
            String resultHash) {

        public ResolutionLineFact {
            periodId = text("periodId", periodId);
            currency = text("currency", currency);
            postingReference = text("postingReference", postingReference);
            sourceLineResultHash = hash("sourceLineResultHash", sourceLineResultHash);
            resultHash = hash("resultHash", resultHash);
            if (sourceAccountingPeriod == null || targetAccountingPeriod == null || direction == null
                    || direction == MaintenanceBalanceDirection.NONE || differenceAmount == null
                    || differenceAmount.signum() <= 0) {
                throw invalid("line", "Billing关闭期间处理明细不完整");
            }
        }
    }

    private static String text(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static String hash(String field, String value) {
        String result = text(field, value).toLowerCase();
        if (!result.matches("[0-9a-f]{64}")) {
            throw invalid(field, "字段必须为SHA-256");
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException(
                "BillingRetroactivePeriodResolutionPort", field, message);
    }
}
