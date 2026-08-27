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
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 写入费用任务的 Billing 入账检查点。 */
public record MaintenanceBillingPostingEvidence(
        String postingId,
        String adjustmentId,
        String resultHash,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        MaintenanceBillingPostingStatus status,
        int commissionAdjustmentCount,
        LocalDateTime recordedAt) {

    public MaintenanceBillingPostingEvidence {
        postingId = requireText("postingId", postingId);
        adjustmentId = requireText("adjustmentId", adjustmentId);
        resultHash = requireHash("resultHash", resultHash);
        currency = requireText("currency", currency).toUpperCase(Locale.ROOT);
        if (direction == null || amount == null || status == null || recordedAt == null
                || amount.signum() < 0 || commissionAdjustmentCount < 0) {
            throw validation("posting", "Billing 入账证据不完整或不合法");
        }
        if ((direction == MaintenanceBalanceDirection.NONE) != (amount.signum() == 0)) {
            throw validation("amount", "无差额方向必须对应零金额，借贷方向必须对应正金额");
        }
    }

    public String contentHash() {
        return sha256(String.join("|", postingId, adjustmentId, resultHash, direction.getCode(),
                amount.toPlainString(), currency, status.getCode(),
                Integer.toString(commissionAdjustmentCount), recordedAt.toString()));
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException(
                "MaintenanceBillingPostingEvidence", fieldName, message);
    }
}
