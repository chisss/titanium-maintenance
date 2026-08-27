package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.ProductSurrenderValuePort.SurrenderFact;

/** 写入费用任务事件的不可变 Product 报价证据。 */
public record MaintenancePremiumQuoteEvidence(
        MaintenancePremiumQuoteStatus status,
        String quoteId,
        String quoteVersion,
        String requestPayloadHash,
        String originalCalculationId,
        String originalResultHash,
        String replacementCalculationId,
        String replacementResultHash,
        String pricingPlanVersion,
        String pricingPlanContentHash,
        String resultHash,
        String detailSummary,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        LocalDateTime quotedAt,
        LocalDateTime validUntil) {

    public MaintenancePremiumQuoteEvidence {
        if (status == null) {
            throw validation("status", "报价状态不能为空");
        }
        detailSummary = requireText("detailSummary", detailSummary);
        if (status == MaintenancePremiumQuoteStatus.QUOTED) {
            quoteId = requireText("quoteId", quoteId);
            quoteVersion = requireText("quoteVersion", quoteVersion);
            requestPayloadHash = requireHash("requestPayloadHash", requestPayloadHash);
            originalCalculationId = requireText("originalCalculationId", originalCalculationId);
            originalResultHash = requireHash("originalResultHash", originalResultHash);
            replacementCalculationId = requireText("replacementCalculationId", replacementCalculationId);
            replacementResultHash = requireHash("replacementResultHash", replacementResultHash);
            pricingPlanVersion = requireText("pricingPlanVersion", pricingPlanVersion);
            pricingPlanContentHash = requireHash("pricingPlanContentHash", pricingPlanContentHash);
            resultHash = requireHash("resultHash", resultHash);
            currency = requireText("currency", currency).toUpperCase(Locale.ROOT);
            if (direction == null || amount == null || amount.signum() < 0 || quotedAt == null
                    || validUntil == null || !validUntil.isAfter(quotedAt)) {
                throw validation("quote", "Product 报价证据不完整或不合法");
            }
        } else {
            if (quotedAt == null) {
                throw validation("quotedAt", "无需报价的判定时间不能为空");
            }
            if (quoteId != null || quoteVersion != null || requestPayloadHash != null
                    || originalCalculationId != null || originalResultHash != null
                    || replacementCalculationId != null || replacementResultHash != null
                    || pricingPlanVersion != null || pricingPlanContentHash != null || resultHash != null
                    || validUntil != null) {
                throw validation("notRequired", "无需报价不能携带 Product 报价事实");
            }
            direction = MaintenanceBalanceDirection.NONE;
            amount = BigDecimal.ZERO;
            currency = null;
        }
    }

    public static MaintenancePremiumQuoteEvidence notRequired(String reason, LocalDateTime decidedAt) {
        return new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.NOT_REQUIRED, null, null, null, null, null, null, null,
                null, null, null, reason, MaintenanceBalanceDirection.NONE, BigDecimal.ZERO,
                null, decidedAt, null);
    }

    /** 将 Product 退保现金价值转换为统一的费用任务报价证据。 */
    public static MaintenancePremiumQuoteEvidence fromSurrender(
            SurrenderFact fact,
            LocalDateTime quotedAt) {
        if (fact == null) {
            throw validation("surrenderFact", "退保价值事实不能为空");
        }
        String summary = String.format(
                Locale.ROOT, "SURRENDER %s %s %s; refundType=%s; cashValueRate=%s",
                fact.direction().getCode(), fact.amount().stripTrailingZeros().toPlainString(),
                fact.currency(), fact.refundType(), fact.cashValueRate().stripTrailingZeros().toPlainString());
        return new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED,
                fact.adjustmentId(),
                "SURRENDER:" + fact.policyVersion(),
                fact.requestHash(),
                fact.originalCalculationId(),
                fact.originalResultHash(),
                fact.replacementCalculationId(),
                fact.replacementResultHash(),
                fact.pricingPlanVersion(),
                fact.pricingPlanContentHash(),
                fact.adjustmentResultHash(),
                summary,
                fact.direction(),
                fact.amount(),
                fact.currency(),
                quotedAt,
                quotedAt.plusHours(24));
    }

    public String evidenceVersion() {
        return status == MaintenancePremiumQuoteStatus.QUOTED ? quoteVersion : status.getCode();
    }

    public boolean expiredAt(LocalDateTime businessTime) {
        return status == MaintenancePremiumQuoteStatus.QUOTED
                && (businessTime == null || !businessTime.isBefore(validUntil));
    }

    /** 证据摘要不包含定价因子原值，可安全写入任务操作事实。 */
    public String contentHash() {
        return contentHash(quotedAt, validUntil);
    }

    /** 投影时间按秒持久化；业务字段和秒级时间相同时视为同一报价重放。 */
    public boolean sameIdempotencyFact(MaintenancePremiumQuoteEvidence other) {
        return other != null
                && contentHash(persistenceTime(quotedAt), persistenceTime(validUntil))
                        .equals(other.contentHash(
                                persistenceTime(other.quotedAt),
                                persistenceTime(other.validUntil)));
    }

    private String contentHash(LocalDateTime quoteTime, LocalDateTime expiryTime) {
        String canonical = String.join("|",
                status.getCode(), value(quoteId), value(quoteVersion), value(requestPayloadHash),
                value(originalCalculationId), value(originalResultHash), value(replacementCalculationId),
                value(replacementResultHash), value(pricingPlanVersion), value(pricingPlanContentHash),
                value(resultHash), detailSummary, direction.getCode(), amount.toPlainString(),
                value(currency), quoteTime.toString(), value(expiryTime));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private LocalDateTime persistenceTime(LocalDateTime value) {
        return value == null ? null : value.withNano(0);
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

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenancePremiumQuoteEvidence", fieldName, message);
    }
}
