package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 写入费用任务的 Payment 收款或退款资金检查点。 */
public record MaintenanceFundSettlementEvidence(
        MaintenanceFundSettlementType type,
        MaintenanceFundSettlementStatus status,
        String sourcePostingId,
        String instructionId,
        String orderId,
        String externalStatus,
        BigDecimal amount,
        String currency,
        String failureCode,
        String failureMessage,
        LocalDateTime recordedAt) {

    public MaintenanceFundSettlementEvidence {
        if (type == null || status == null || recordedAt == null) {
            throw validation("fundSettlement", "资金证据类型、状态和记录时间不能为空");
        }
        sourcePostingId = requireText("sourcePostingId", sourcePostingId);
        externalStatus = requireText("externalStatus", externalStatus);
        currency = requireText("currency", currency).toUpperCase(Locale.ROOT);
        instructionId = normalize(instructionId);
        orderId = normalize(orderId);
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (amount == null || amount.signum() < 0) {
            throw validation("amount", "资金金额不能为空或小于零");
        }
        if (type == MaintenanceFundSettlementType.NOT_REQUIRED) {
            if (status != MaintenanceFundSettlementStatus.NOT_REQUIRED || amount.signum() != 0
                    || instructionId != null || orderId != null) {
                throw validation("notRequired", "无需资金处理的证据字段不一致");
            }
        } else if (amount.signum() <= 0) {
            throw validation("amount", "收款或退款金额必须大于零");
        }
        if (type == MaintenanceFundSettlementType.COLLECTION && instructionId != null) {
            throw validation("instructionId", "收款证据不能携带退款指令");
        }
        if (!status.failed() && type != MaintenanceFundSettlementType.NOT_REQUIRED && orderId == null) {
            throw validation("orderId", "非失败资金证据必须携带 Payment 单号");
        }
        if (status.failed() && (failureCode == null || failureMessage == null)) {
            throw validation("failure", "失败资金证据必须携带失败码和原因");
        }
        if (!status.failed() && (failureCode != null || failureMessage != null)) {
            throw validation("failure", "非失败资金证据不能携带失败信息");
        }
    }

    public String evidenceVersion(MaintenanceBillingPostingEvidence posting) {
        return posting.postingId() + ':' + status.getCode();
    }

    public String gateContentHash(MaintenanceBillingPostingEvidence posting) {
        return sha256(posting.contentHash() + '|' + contentHash());
    }

    public String detailSummary() {
        return status.failed()
                ? failureCode + ":" + failureMessage
                : type.getCode() + ':' + externalStatus;
    }

    public String contentHash() {
        return sha256(String.join("|", type.getCode(), status.getCode(), sourcePostingId,
                value(instructionId), value(orderId), externalStatus, amount.toPlainString(), currency,
                value(failureCode), value(failureMessage), recordedAt.toString()));
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
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
                "MaintenanceFundSettlementEvidence", fieldName, message);
    }
}
