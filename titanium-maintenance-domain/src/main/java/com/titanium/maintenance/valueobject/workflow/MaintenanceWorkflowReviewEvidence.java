package com.titanium.maintenance.valueobject.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 审核决定及其可重放、可解释证据。 */
public record MaintenanceWorkflowReviewEvidence(
        MaintenanceReviewMode mode,
        MaintenanceReviewDecision decision,
        String policyCode,
        String policyVersion,
        List<MaintenanceReviewGateEvidence> gates,
        String comment,
        LocalDateTime decidedAt,
        String decidedBy) {

    public MaintenanceWorkflowReviewEvidence {
        if (mode == null || decision == null || decidedAt == null) {
            throw validation("审核方式、结论和时间不能为空");
        }
        policyCode = requireText("policyCode", policyCode);
        policyVersion = requireText("policyVersion", policyVersion);
        comment = requireText("comment", comment);
        decidedBy = requireText("decidedBy", decidedBy);
        gates = gates == null ? List.of() : List.copyOf(gates);
        validateGates(mode, decision, gates);
    }

    /** 生成不包含服务端时间的稳定审核载荷摘要，支持响应丢失后的原载荷重试。 */
    public String contentHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, mode.getCode());
            update(digest, decision.getCode());
            update(digest, policyCode);
            update(digest, policyVersion);
            gates.stream().sorted(Comparator.comparing(gate -> gate.gate().ordinal())).forEach(gate -> {
                update(digest, gate.gate().getCode());
                update(digest, Boolean.toString(gate.passed()));
                update(digest, gate.evidenceHash());
                update(digest, gate.detailCode());
            });
            update(digest, comment);
            update(digest, decidedBy);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK不支持SHA-256", exception);
        }
    }

    private static void validateGates(
            MaintenanceReviewMode mode,
            MaintenanceReviewDecision decision,
            List<MaintenanceReviewGateEvidence> gates) {
        if (gates.stream().anyMatch(gate -> gate == null)) {
            throw validation("审核门禁证据不能包含空项");
        }
        Set<MaintenanceReviewGate> actual = new HashSet<>();
        if (gates.stream().anyMatch(gate -> !actual.add(gate.gate()))) {
            throw validation("自动审核门禁不能重复");
        }
        if (mode == MaintenanceReviewMode.MANUAL && !gates.isEmpty()) {
            throw validation("人工审核不能伪造自动门禁证据");
        }
        if (mode == MaintenanceReviewMode.AUTOMATIC
                && (decision != MaintenanceReviewDecision.APPROVE
                || !actual.equals(EnumSet.allOf(MaintenanceReviewGate.class))
                || gates.stream().anyMatch(gate -> !gate.passed()))) {
            throw validation("自动审核只能在七类门禁全部通过后形成通过结论");
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
                    "MaintenanceWorkflowReviewEvidence", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("MaintenanceWorkflowReviewEvidence", message);
    }
}
