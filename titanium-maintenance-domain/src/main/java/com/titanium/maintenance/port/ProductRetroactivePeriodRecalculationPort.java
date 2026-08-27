package com.titanium.maintenance.port;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;

/** Maintenance 调用 Product 执行追溯期间重算的出口端口。 */
public interface ProductRetroactivePeriodRecalculationPort {

    RecalculationFact recalculate(RecalculationRequest request);

    record RecalculationRequest(
            String tenantId,
            String maintenanceId,
            String policyId,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            String originalCalculationId,
            String replacementCalculationId,
            LocalDateTime scopeFrom,
            LocalDateTime scopeTo,
            List<AffectedPeriod> periods,
            String requestId) {

        public RecalculationRequest {
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            analysisId = text("analysisId", analysisId);
            analysisResultHash = hash("analysisResultHash", analysisResultHash);
            originalCalculationId = text("originalCalculationId", originalCalculationId);
            replacementCalculationId = text("replacementCalculationId", replacementCalculationId);
            requestId = text("requestId", requestId);
            periods = periods == null ? List.of() : List.copyOf(periods);
            if (analysisVersion < 1 || scopeFrom == null || scopeTo == null || !scopeFrom.isBefore(scopeTo)) {
                throw invalid("request", "Product期间重算请求范围或分析版本非法");
            }
        }

        public String payloadHash() {
            StringJoiner periodValues = new StringJoiner(";");
            periods.stream().sorted(Comparator.comparing(AffectedPeriod::periodStart)
                    .thenComparing(AffectedPeriod::periodId)).forEach(period -> periodValues.add(String.join(",",
                            period.periodId(), period.sourceReferenceId(), period.periodStart().toString(),
                            amount(period.originalAmount()), period.currency(), period.sourceEvidenceHash())));
            return sha256(String.join("|", tenantId, maintenanceId, policyId, analysisId,
                    String.valueOf(analysisVersion), analysisResultHash, originalCalculationId,
                    replacementCalculationId, scopeFrom.toString(), scopeTo.toString(), requestId,
                    periodValues.toString()));
        }
    }

    record AffectedPeriod(
            String periodId,
            String sourceReferenceId,
            LocalDateTime periodStart,
            BigDecimal originalAmount,
            String currency,
            String sourceEvidenceHash) {

        public AffectedPeriod {
            periodId = text("periodId", periodId);
            sourceReferenceId = text("sourceReferenceId", sourceReferenceId);
            currency = text("currency", currency).toUpperCase(Locale.ROOT);
            sourceEvidenceHash = hash("sourceEvidenceHash", sourceEvidenceHash);
            if (periodStart == null || originalAmount == null || originalAmount.signum() < 0) {
                throw invalid("period", "受影响期间时间或金额非法");
            }
        }
    }

    record RecalculationFact(
            String tenantId,
            String maintenanceId,
            String policyId,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            String recalculationId,
            String recalculationVersion,
            String requestId,
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

        public RecalculationFact {
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            analysisId = text("analysisId", analysisId);
            analysisResultHash = hash("analysisResultHash", analysisResultHash);
            recalculationId = text("recalculationId", recalculationId);
            recalculationVersion = text("recalculationVersion", recalculationVersion);
            requestId = text("requestId", requestId);
            originalCalculationId = text("originalCalculationId", originalCalculationId);
            originalResultHash = hash("originalResultHash", originalResultHash);
            replacementCalculationId = text("replacementCalculationId", replacementCalculationId);
            replacementResultHash = hash("replacementResultHash", replacementResultHash);
            currency = text("currency", currency).toUpperCase(Locale.ROOT);
            inputHash = hash("inputHash", inputHash);
            resultHash = hash("resultHash", resultHash);
            periods = periods == null ? List.of() : List.copyOf(periods);
            if (analysisVersion < 1 || direction == null || amount == null || amount.signum() < 0
                    || calculatedAt == null) {
                throw invalid("fact", "Product期间重算事实不完整");
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
        String result = text(field, value).toLowerCase(Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) {
            throw invalid(field, "字段必须为SHA-256");
        }
        return result;
    }

    private static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256", exception);
        }
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("ProductRetroactivePeriodRecalculationPort", field, message);
    }
}
