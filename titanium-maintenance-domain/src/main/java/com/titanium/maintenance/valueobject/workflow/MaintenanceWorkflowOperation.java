package com.titanium.maintenance.valueobject.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 一次任务操作及其服务端计算的幂等指纹。 */
public record MaintenanceWorkflowOperation(
        String operationId,
        MaintenanceWorkflowAction action,
        String payloadHash,
        String evidenceVersion,
        String evidenceHash,
        String resultCode,
        String reason,
        LocalDateTime operatedAt,
        String operatedBy) {

    public MaintenanceWorkflowOperation {
        operationId = requireText("operationId", operationId);
        payloadHash = requireText("payloadHash", payloadHash).toLowerCase();
        operatedBy = requireText("operatedBy", operatedBy);
        evidenceVersion = normalize(evidenceVersion);
        evidenceHash = normalize(evidenceHash);
        resultCode = normalize(resultCode);
        reason = normalize(reason);
        if (action == null || operatedAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowOperation", "操作类型和操作时间不能为空");
        }
        if (!payloadHash.matches("[0-9a-f]{64}")) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowOperation", "payloadHash", "操作摘要必须为SHA-256");
        }
        if (evidenceHash != null && !evidenceHash.matches("[0-9a-fA-F]{64}")) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowOperation", "evidenceHash", "证据摘要必须为SHA-256");
        }
        evidenceHash = evidenceHash == null ? null : evidenceHash.toLowerCase();
    }

    public static MaintenanceWorkflowOperation create(
            String operationId,
            MaintenanceWorkflowAction action,
            String taskId,
            String evidenceVersion,
            String evidenceHash,
            String resultCode,
            String reason,
            LocalDateTime operatedAt,
            String operatedBy) {
        String hash = sha256(action, taskId, operatedBy, evidenceVersion, evidenceHash, resultCode, reason);
        return new MaintenanceWorkflowOperation(
                operationId, action, hash, evidenceVersion, evidenceHash,
                resultCode, reason, operatedAt, operatedBy);
    }

    private static String sha256(MaintenanceWorkflowAction action, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, action == null ? null : action.getCode());
            for (String value : values) {
                update(digest, value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK不支持SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowOperation", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
