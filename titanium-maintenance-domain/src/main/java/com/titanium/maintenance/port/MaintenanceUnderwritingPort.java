package com.titanium.maintenance.port;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** Maintenance 调用 Underwriting 取得权威风险结论的出口端口。 */
public interface MaintenanceUnderwritingPort {

    AssessmentFact assess(AssessmentRequest request);

    /** 核保请求只携带案件冻结事实与规范化风险字段差异。 */
    record AssessmentRequest(
            String tenantId,
            String maintenanceId,
            String policyId,
            Long policyBaselineVersion,
            String productId,
            String productVersion,
            String planVersion,
            String itemCode,
            String configurationVersion,
            String configurationContentHash,
            boolean configurationRequiresUnderwriting,
            List<RiskFieldChange> riskFieldChanges,
            String idempotencyKey,
            String requestedBy) {

        public AssessmentRequest {
            tenantId = requireText("tenantId", tenantId);
            maintenanceId = requireText("maintenanceId", maintenanceId);
            policyId = requireText("policyId", policyId);
            productId = requireText("productId", productId);
            productVersion = requireText("productVersion", productVersion);
            planVersion = normalize(planVersion);
            itemCode = requireText("itemCode", itemCode);
            configurationVersion = requireText("configurationVersion", configurationVersion);
            configurationContentHash = requireHash("configurationContentHash", configurationContentHash);
            idempotencyKey = requireText("idempotencyKey", idempotencyKey);
            requestedBy = requireText("requestedBy", requestedBy);
            riskFieldChanges = riskFieldChanges == null ? List.of() : List.copyOf(riskFieldChanges);
            if (policyBaselineVersion == null || policyBaselineVersion < 0) {
                throw validation("policyBaselineVersion", "保单基准版本不合法");
            }
        }

        public String payloadHash() {
            StringBuilder canonical = new StringBuilder();
            append(canonical, tenantId);
            append(canonical, maintenanceId);
            append(canonical, policyId);
            append(canonical, policyBaselineVersion.toString());
            append(canonical, productId);
            append(canonical, productVersion);
            append(canonical, planVersion);
            append(canonical, itemCode);
            append(canonical, configurationVersion);
            append(canonical, configurationContentHash);
            append(canonical, Boolean.toString(configurationRequiresUnderwriting));
            append(canonical, idempotencyKey);
            riskFieldChanges.stream()
                    .sorted(Comparator.comparing(RiskFieldChange::fieldCode)
                            .thenComparing(change -> nullToEmpty(change.objectId())))
                    .forEach(change -> {
                        append(canonical, change.objectId());
                        append(canonical, change.fieldCode());
                        append(canonical, change.dataType());
                        append(canonical, change.beforeValue());
                        append(canonical, change.proposedValue());
                        append(canonical, change.changeTypeCode());
                    });
            return sha256(canonical.toString());
        }
    }

    record RiskFieldChange(
            String objectId,
            String fieldCode,
            String dataType,
            String beforeValue,
            String proposedValue,
            String changeTypeCode) {

        public RiskFieldChange {
            objectId = normalize(objectId);
            fieldCode = requireText("fieldCode", fieldCode);
            dataType = requireText("dataType", dataType);
            changeTypeCode = requireText("changeTypeCode", changeTypeCode);
        }
    }

    /** 防腐层完成回显校验后交给 Application 的核保事实。 */
    record AssessmentFact(
            String underwritingCaseId,
            String idempotencyKey,
            String payloadHash,
            String ruleVersion,
            String modelVersion,
            MaintenanceUnderwritingConclusion conclusion,
            List<String> additionalConditions,
            String summary,
            LocalDateTime completedAt) {

        public AssessmentFact {
            underwritingCaseId = requireText("underwritingCaseId", underwritingCaseId);
            idempotencyKey = requireText("idempotencyKey", idempotencyKey);
            payloadHash = requireHash("payloadHash", payloadHash);
            ruleVersion = requireText("ruleVersion", ruleVersion);
            modelVersion = requireText("modelVersion", modelVersion);
            summary = requireText("summary", summary);
            additionalConditions = additionalConditions == null ? List.of() : List.copyOf(additionalConditions);
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
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String requireHash(String fieldName, String value) {
        String normalized = requireText(fieldName, value).toLowerCase();
        if (!Pattern.matches("[0-9a-f]{64}", normalized)) {
            throw validation(fieldName, "字段必须为SHA-256摘要");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void append(StringBuilder target, String value) {
        String normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
        return new MaintenanceValidationException("MaintenanceUnderwritingPort", fieldName, message);
    }
}
