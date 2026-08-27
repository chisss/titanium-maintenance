package com.titanium.maintenance.valueobject.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 写入案件任务事件的不可变核保证据。 */
public record MaintenanceUnderwritingEvidence(
        String underwritingCaseId,
        String requestPayloadHash,
        String ruleVersion,
        String modelVersion,
        MaintenanceUnderwritingConclusion conclusion,
        List<String> additionalConditions,
        String summary,
        LocalDateTime completedAt) {

    public MaintenanceUnderwritingEvidence {
        underwritingCaseId = requireText("underwritingCaseId", underwritingCaseId);
        requestPayloadHash = requireText("requestPayloadHash", requestPayloadHash).toLowerCase();
        ruleVersion = requireText("ruleVersion", ruleVersion);
        modelVersion = requireText("modelVersion", modelVersion);
        summary = requireText("summary", summary);
        additionalConditions = additionalConditions == null ? List.of() : List.copyOf(additionalConditions);
        if (!Pattern.matches("[0-9a-f]{64}", requestPayloadHash)) {
            throw validation("requestPayloadHash", "请求摘要必须为SHA-256");
        }
        if (conclusion == null) {
            throw validation("conclusion", "核保结论不能为空");
        }
        if (conclusion == MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED
                && additionalConditions.isEmpty()) {
            throw validation("additionalConditions", "附加条件通过必须包含条件");
        }
        if (conclusion != MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED
                && !additionalConditions.isEmpty()) {
            throw validation("additionalConditions", "仅附加条件通过可以携带条件");
        }
        if (conclusion.completed() != (completedAt != null)) {
            throw validation("completedAt", "完成时间与核保结论状态不一致");
        }
    }

    /** 核保结果证据摘要不包含字段原值，可安全写入操作事实。 */
    public String contentHash() {
        String canonical = String.join("|",
                underwritingCaseId,
                requestPayloadHash,
                ruleVersion,
                modelVersion,
                conclusion.getCode(),
                String.join(",", additionalConditions),
                summary,
                completedAt == null ? "" : completedAt.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256算法", exception);
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceUnderwritingEvidence", fieldName, message);
    }
}
