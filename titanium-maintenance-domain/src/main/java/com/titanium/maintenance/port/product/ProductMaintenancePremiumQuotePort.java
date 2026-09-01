package com.titanium.maintenance.port.product;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Maintenance 调用 Product 取得保全版本化报价的出口端口。 */
public interface ProductMaintenancePremiumQuotePort {

    QuoteFact quote(QuoteRequest request);

    /** 报价请求只接受定价输入，最终方向和金额由 Product 决定。 */
    record QuoteRequest(
            String tenantId,
            String productId,
            String maintenanceId,
            String policyId,
            long policyBaselineVersion,
            String itemCode,
            String productVersion,
            String planVersion,
            String lifecycleType,
            SnapshotReference beforeSnapshot,
            SnapshotReference proposedSnapshot,
            String originalCalculationId,
            LocalDateTime businessTime,
            String currency,
            BigDecimal sumInsured,
            int age,
            String gender,
            int paymentTermYears,
            int coverageTermYears,
            int paymentPeriods,
            Map<String, Object> pricingFactors,
            List<UnderwritingAdjustment> underwritingAdjustments,
            String channelId,
            int policyYear,
            String reason,
            String idempotencyKey) {

        public QuoteRequest {
            tenantId = requireText("tenantId", tenantId);
            productId = requireText("productId", productId);
            maintenanceId = requireText("maintenanceId", maintenanceId);
            policyId = requireText("policyId", policyId);
            itemCode = requireText("itemCode", itemCode);
            productVersion = requireText("productVersion", productVersion);
            planVersion = requireText("planVersion", planVersion);
            lifecycleType = requireText("lifecycleType", lifecycleType);
            originalCalculationId = requireText("originalCalculationId", originalCalculationId);
            currency = requireText("currency", currency).toUpperCase(Locale.ROOT);
            gender = requireText("gender", gender);
            reason = requireText("reason", reason);
            idempotencyKey = requireText("idempotencyKey", idempotencyKey);
            channelId = normalize(channelId);
            pricingFactors = pricingFactors == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(pricingFactors));
            underwritingAdjustments = underwritingAdjustments == null
                    ? List.of()
                    : List.copyOf(underwritingAdjustments);
            if (policyBaselineVersion < 0 || beforeSnapshot == null || proposedSnapshot == null
                    || businessTime == null || sumInsured == null || sumInsured.signum() <= 0
                    || age < 0 || age > 120 || paymentTermYears <= 0 || coverageTermYears <= 0
                    || paymentPeriods <= 0 || policyYear <= 0) {
                throw validation("pricingInput", "报价定价输入不完整或不合法");
            }
            if (beforeSnapshot.policyVersion() != policyBaselineVersion
                    || proposedSnapshot.policyVersion() != policyBaselineVersion) {
                throw validation("snapshots", "报价快照版本必须与保单基准版本一致");
            }
        }

        /** 与 Product 正式 API 共同遵守的确定性载荷摘要。 */
        public String payloadHash() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("productId", productId);
            payload.put("maintenanceId", maintenanceId);
            payload.put("policyId", policyId);
            payload.put("policyBaselineVersion", policyBaselineVersion);
            payload.put("itemCode", itemCode);
            payload.put("productVersion", productVersion);
            payload.put("planVersion", planVersion);
            payload.put("lifecycleType", lifecycleType);
            payload.put("beforeSnapshot", snapshot(beforeSnapshot));
            payload.put("proposedSnapshot", snapshot(proposedSnapshot));
            payload.put("originalCalculationId", originalCalculationId);
            payload.put("businessTime", businessTime);
            payload.put("currency", currency);
            payload.put("sumInsured", sumInsured);
            payload.put("age", age);
            payload.put("gender", gender);
            payload.put("paymentTermYears", paymentTermYears);
            payload.put("coverageTermYears", coverageTermYears);
            payload.put("paymentPeriods", paymentPeriods);
            payload.put("pricingFactors", pricingFactors);
            payload.put("underwritingAdjustments", adjustments(underwritingAdjustments));
            payload.put("channelId", channelId);
            payload.put("policyYear", policyYear);
            payload.put("reason", reason);
            payload.put("idempotencyKey", idempotencyKey);
            return sha256(canonicalValue(payload));
        }
    }

    record SnapshotReference(
            String storageKey,
            String contentHash,
            long policyVersion,
            OffsetDateTime capturedAt) {

        public SnapshotReference {
            storageKey = requireText("storageKey", storageKey);
            contentHash = requireHash("contentHash", contentHash);
            if (policyVersion < 0 || capturedAt == null) {
                throw validation("snapshot", "快照版本或采集时间不合法");
            }
        }
    }

    record UnderwritingAdjustment(
            String adjustmentCode,
            String type,
            BigDecimal value,
            String reason,
            String ruleVersion) {

        public UnderwritingAdjustment {
            adjustmentCode = requireText("adjustmentCode", adjustmentCode);
            type = requireText("type", type);
            reason = normalize(reason);
            ruleVersion = normalize(ruleVersion);
            if (value == null || value.signum() < 0) {
                throw validation("adjustmentValue", "核保调整值不合法");
            }
        }
    }

    /** Product 防腐层完成逐字段回显校验后返回的报价事实。 */
    record QuoteFact(
            String tenantId,
            String maintenanceId,
            String policyId,
            long policyBaselineVersion,
            String productId,
            String productVersion,
            String planVersion,
            String itemCode,
            String beforeSnapshotHash,
            String proposedSnapshotHash,
            String quoteId,
            String quoteVersion,
            String originalCalculationId,
            String originalResultHash,
            String replacementCalculationId,
            String replacementResultHash,
            String pricingPlanVersion,
            String pricingPlanContentHash,
            String idempotencyKey,
            String payloadHash,
            String resultHash,
            String detailSummary,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            LocalDateTime quotedAt,
            LocalDateTime validUntil) {

        public QuoteFact {
            tenantId = requireText("tenantId", tenantId);
            maintenanceId = requireText("maintenanceId", maintenanceId);
            policyId = requireText("policyId", policyId);
            productId = requireText("productId", productId);
            productVersion = requireText("productVersion", productVersion);
            planVersion = requireText("planVersion", planVersion);
            itemCode = requireText("itemCode", itemCode);
            beforeSnapshotHash = requireHash("beforeSnapshotHash", beforeSnapshotHash);
            proposedSnapshotHash = requireHash("proposedSnapshotHash", proposedSnapshotHash);
            quoteId = requireText("quoteId", quoteId);
            quoteVersion = requireText("quoteVersion", quoteVersion);
            originalCalculationId = requireText("originalCalculationId", originalCalculationId);
            originalResultHash = requireHash("originalResultHash", originalResultHash);
            replacementCalculationId = requireText("replacementCalculationId", replacementCalculationId);
            replacementResultHash = requireHash("replacementResultHash", replacementResultHash);
            pricingPlanVersion = requireText("pricingPlanVersion", pricingPlanVersion);
            pricingPlanContentHash = requireHash("pricingPlanContentHash", pricingPlanContentHash);
            idempotencyKey = requireText("idempotencyKey", idempotencyKey);
            payloadHash = requireHash("payloadHash", payloadHash);
            resultHash = requireHash("resultHash", resultHash);
            detailSummary = requireText("detailSummary", detailSummary);
            currency = requireText("currency", currency).toUpperCase(Locale.ROOT);
            if (policyBaselineVersion < 0 || direction == null || amount == null || amount.signum() < 0
                    || quotedAt == null || validUntil == null || !validUntil.isAfter(quotedAt)) {
                throw validation("quoteFact", "Product 报价事实不完整或不合法");
            }
        }
    }

    private static List<Map<String, Object>> adjustments(List<UnderwritingAdjustment> requests) {
        return requests.stream().map(request -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("adjustmentCode", request.adjustmentCode());
            value.put("type", request.type());
            value.put("value", request.value());
            value.put("reason", request.reason());
            value.put("ruleVersion", request.ruleVersion());
            return value;
        }).toList();
    }

    private static Map<String, Object> snapshot(SnapshotReference reference) {
        return Map.of(
                "storageKey", reference.storageKey(),
                "contentHash", reference.contentHash(),
                "policyVersion", reference.policyVersion(),
                "capturedAt", reference.capturedAt().toString());
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "*";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            map.entrySet().stream()
                    .sorted((left, right) -> String.valueOf(left.getKey())
                            .compareTo(String.valueOf(right.getKey())))
                    .forEach(entry -> joiner.add(
                            String.valueOf(entry.getKey()) + ':' + canonicalValue(entry.getValue())));
            return joiner.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            iterable.forEach(item -> joiner.add(canonicalValue(item)));
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String requireHash(String fieldName, String value) {
        String normalized = requireText(fieldName, value).toLowerCase(Locale.ROOT);
        if (!Pattern.matches("[0-9a-f]{64}", normalized)) {
            throw validation(fieldName, "字段必须为SHA-256摘要");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("ProductMaintenancePremiumQuotePort", fieldName, message);
    }
}
