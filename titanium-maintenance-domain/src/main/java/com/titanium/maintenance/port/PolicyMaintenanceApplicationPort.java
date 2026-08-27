package com.titanium.maintenance.port;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;

/** Maintenance 调用 Policy 正式合同应用能力的出口端口。 */
public interface PolicyMaintenanceApplicationPort {

    ApplicationFact apply(ApplicationRequest request);

    record ApplicationRequest(
            String tenantId,
            String policyId,
            String requestId,
            String maintenanceCaseId,
            long expectedPolicyVersion,
            String requestPayloadHash,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<FieldChange> changes,
            PolicyMaintenanceAction stateAction,
            String stateReason,
            TerminationReason terminationReason,
            RetroactiveEvidence retroactiveEvidence,
            String operatorId) {

        public ApplicationRequest {
            tenantId = requireText(tenantId, "tenantId");
            policyId = requireText(policyId, "policyId");
            requestId = requireText(requestId, "requestId");
            maintenanceCaseId = requireText(maintenanceCaseId, "maintenanceCaseId");
            proposedSnapshotHash = requireHash(proposedSnapshotHash, "proposedSnapshotHash");
            effectiveTimeType = requireText(effectiveTimeType, "effectiveTimeType");
            changeSummary = requireText(changeSummary, "changeSummary");
            operatorId = requireText(operatorId, "operatorId");
            stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
            if (expectedPolicyVersion < 0 || effectiveAt == null || changes == null
                    || (changes.isEmpty() && !stateAction.changesStatus())) {
                throw validation("Policy 应用请求的版本、生效时间、字段变更或状态动作不完整");
            }
            changes = List.copyOf(changes);
            validateStateChange(stateAction, stateReason, terminationReason);
            if ("RETROACTIVE".equals(effectiveTimeType) && retroactiveEvidence == null) {
                throw validation("追溯Policy应用请求缺少跨域证据");
            }
            if (!"RETROACTIVE".equals(effectiveTimeType) && retroactiveEvidence != null) {
                throw validation("非追溯Policy应用请求不得携带追溯证据");
            }
            String calculated = calculateHash(
                    tenantId, policyId, requestId, maintenanceCaseId, expectedPolicyVersion,
                    proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                    stateAction, stateReason, terminationReason, retroactiveEvidence);
            if (requestPayloadHash != null && !requestPayloadHash.isBlank()
                    && !calculated.equalsIgnoreCase(requestPayloadHash)) {
                throw validation("Policy 应用请求摘要与结构化载荷不一致");
            }
            requestPayloadHash = calculated;
        }

        /** 兼容 M5-02 仅结构化字段请求。 */
        public ApplicationRequest(
                String tenantId,
                String policyId,
                String requestId,
                String maintenanceCaseId,
                long expectedPolicyVersion,
                String requestPayloadHash,
                String proposedSnapshotHash,
                String effectiveTimeType,
                LocalDateTime effectiveAt,
                String changeSummary,
                List<FieldChange> changes,
                String operatorId) {
            this(tenantId, policyId, requestId, maintenanceCaseId, expectedPolicyVersion,
                    requestPayloadHash, proposedSnapshotHash, effectiveTimeType, effectiveAt,
                    changeSummary, changes, PolicyMaintenanceAction.NONE, null, null, null, operatorId);
        }

        /** 兼容 M5-03 状态类请求。 */
        public ApplicationRequest(
                String tenantId,
                String policyId,
                String requestId,
                String maintenanceCaseId,
                long expectedPolicyVersion,
                String requestPayloadHash,
                String proposedSnapshotHash,
                String effectiveTimeType,
                LocalDateTime effectiveAt,
                String changeSummary,
                List<FieldChange> changes,
                PolicyMaintenanceAction stateAction,
                String stateReason,
                TerminationReason terminationReason,
                String operatorId) {
            this(tenantId, policyId, requestId, maintenanceCaseId, expectedPolicyVersion,
                    requestPayloadHash, proposedSnapshotHash, effectiveTimeType, effectiveAt,
                    changeSummary, changes, stateAction, stateReason, terminationReason, null, operatorId);
        }

        private static String calculateHash(
                String tenantId,
                String policyId,
                String requestId,
                String maintenanceCaseId,
                long expectedPolicyVersion,
                String proposedSnapshotHash,
                String effectiveTimeType,
                LocalDateTime effectiveAt,
                String changeSummary,
                List<FieldChange> changes,
                PolicyMaintenanceAction stateAction,
                String stateReason,
                TerminationReason terminationReason,
                RetroactiveEvidence retroactiveEvidence) {
            StringBuilder canonical = new StringBuilder();
            append(canonical, tenantId);
            append(canonical, policyId);
            append(canonical, requestId);
            append(canonical, maintenanceCaseId);
            append(canonical, Long.toString(expectedPolicyVersion));
            append(canonical, proposedSnapshotHash);
            append(canonical, effectiveTimeType);
            append(canonical, effectiveAt.toString());
            append(canonical, changeSummary);
            changes.stream()
                    .sorted(Comparator.comparing(FieldChange::fieldCode).thenComparing(FieldChange::objectId))
                    .forEach(field -> {
                        append(canonical, field.itemCode());
                        append(canonical, field.objectId());
                        append(canonical, field.fieldCode());
                        append(canonical, field.dataType());
                        append(canonical, field.canonicalValue());
                    });
            if (stateAction.changesStatus()) {
                append(canonical, stateAction.name());
                append(canonical, stateReason);
                append(canonical, terminationReason == null ? null : terminationReason.getCode());
            }
            appendRetroactiveEvidence(canonical, retroactiveEvidence);
            return sha256(canonical.toString());
        }

        private static void appendRetroactiveEvidence(
                StringBuilder canonical,
                RetroactiveEvidence evidence) {
            if (evidence == null) {
                return;
            }
            append(canonical, evidence.analysisId());
            append(canonical, Integer.toString(evidence.analysisVersion()));
            append(canonical, evidence.analysisResultHash());
            append(canonical, evidence.periodRecalculationId());
            append(canonical, Integer.toString(evidence.periodRecalculationVersion()));
            append(canonical, evidence.productRecalculationId());
            append(canonical, evidence.productRecalculationVersion());
            append(canonical, evidence.productInputHash());
            append(canonical, evidence.productResultHash());
            append(canonical, evidence.billingBatchId());
            append(canonical, evidence.billingBatchResultHash());
            append(canonical, evidence.billingStatus());
            append(canonical, evidence.billingResolutionId());
            append(canonical, evidence.billingResolutionResultHash());
            append(canonical, evidence.targetAccountingPeriod());
            append(canonical, Integer.toString(evidence.resolvedLineCount()));
        }

        private static void validateStateChange(
                PolicyMaintenanceAction action,
                String reason,
                TerminationReason terminationReason) {
            if (!action.changesStatus()) {
                if ((reason != null && !reason.isBlank()) || terminationReason != null) {
                    throw validation("无状态动作时不得提交状态原因或终止原因");
                }
                return;
            }
            if (reason == null || reason.isBlank()) {
                throw validation("状态类保全必须包含状态变更原因");
            }
            if (action == PolicyMaintenanceAction.TERMINATE && terminationReason == null) {
                throw validation("终止保单必须包含终止原因");
            }
            if (action != PolicyMaintenanceAction.TERMINATE && terminationReason != null) {
                throw validation("非终止动作不得包含终止原因");
            }
        }
    }

    record FieldChange(
            String itemCode,
            String objectId,
            String fieldCode,
            String dataType,
            String canonicalValue) {

        public FieldChange {
            itemCode = requireText(itemCode, "itemCode");
            objectId = requireText(objectId, "objectId");
            fieldCode = requireText(fieldCode, "fieldCode");
            dataType = requireText(dataType, "dataType").toUpperCase();
        }
    }

    record RetroactiveEvidence(
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            String periodRecalculationId,
            int periodRecalculationVersion,
            String productRecalculationId,
            String productRecalculationVersion,
            String productInputHash,
            String productResultHash,
            String billingBatchId,
            String billingBatchResultHash,
            String billingStatus,
            String billingResolutionId,
            String billingResolutionResultHash,
            String targetAccountingPeriod,
            int resolvedLineCount) {

        public RetroactiveEvidence {
            analysisId = requireText(analysisId, "analysisId");
            analysisResultHash = requireHash(analysisResultHash, "analysisResultHash");
            periodRecalculationId = requireText(periodRecalculationId, "periodRecalculationId");
            productRecalculationId = requireText(productRecalculationId, "productRecalculationId");
            productRecalculationVersion = requireText(productRecalculationVersion, "productRecalculationVersion");
            productInputHash = requireHash(productInputHash, "productInputHash");
            productResultHash = requireHash(productResultHash, "productResultHash");
            billingBatchId = requireText(billingBatchId, "billingBatchId");
            billingBatchResultHash = requireHash(billingBatchResultHash, "billingBatchResultHash");
            billingStatus = requireText(billingStatus, "billingStatus");
            billingResolutionId = normalize(billingResolutionId);
            billingResolutionResultHash = normalizeHash(billingResolutionResultHash);
            targetAccountingPeriod = normalize(targetAccountingPeriod);
            if (analysisVersion < 1 || periodRecalculationVersion < 1 || resolvedLineCount < 0) {
                throw validation("追溯证据版本或处理行数非法");
            }
            boolean reviewRequired = "REVIEW_REQUIRED".equals(billingStatus);
            if (reviewRequired && (billingResolutionId == null || billingResolutionResultHash == null
                    || targetAccountingPeriod == null || resolvedLineCount < 1)) {
                throw validation("关闭期间批次缺少完整处理结论");
            }
            if (!reviewRequired && (billingResolutionId != null || billingResolutionResultHash != null
                    || targetAccountingPeriod != null || resolvedLineCount != 0)) {
                throw validation("无关闭期间的批次不得携带处理结论");
            }
        }
    }

    record ApplicationFact(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            AppliedSnapshot appliedSnapshot,
            List<AppliedField> appliedFields,
            LocalDateTime appliedAt,
            PolicyMaintenanceAction stateAction,
            String statusBefore,
            String statusAfter,
            RetroactiveEvidence retroactiveEvidence) {

        public ApplicationFact {
            appliedFields = appliedFields == null ? List.of() : List.copyOf(appliedFields);
            stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
        }

        /** 兼容 M5-02 字段型回执。 */
        public ApplicationFact(
                String requestId,
                String endorsementNo,
                long expectedPolicyVersion,
                long actualPolicyVersion,
                String applicationHash,
                AppliedSnapshot appliedSnapshot,
                List<AppliedField> appliedFields,
                LocalDateTime appliedAt) {
            this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion, applicationHash,
                    appliedSnapshot, appliedFields, appliedAt, PolicyMaintenanceAction.NONE, null, null, null);
        }

        /** 兼容 M5-03 状态类回执。 */
        public ApplicationFact(
                String requestId,
                String endorsementNo,
                long expectedPolicyVersion,
                long actualPolicyVersion,
                String applicationHash,
                AppliedSnapshot appliedSnapshot,
                List<AppliedField> appliedFields,
                LocalDateTime appliedAt,
                PolicyMaintenanceAction stateAction,
                String statusBefore,
                String statusAfter) {
            this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion, applicationHash,
                    appliedSnapshot, appliedFields, appliedAt, stateAction, statusBefore, statusAfter, null);
        }
    }

    record AppliedSnapshot(
            String storageKey,
            String contentHash,
            long policyVersion,
            OffsetDateTime capturedAt) {
    }

    record AppliedField(
            String itemCode,
            String objectId,
            String fieldCode,
            String dataType,
            String canonicalValue) {
    }

    static String stableRequestId(String tenantId, String maintenanceId, String taskId) {
        return "effect-" + sha256(tenantId + "\n" + maintenanceId + "\n" + taskId);
    }

    static String stableCaseRequestId(String tenantId, String maintenanceId) {
        return "effect-case-" + sha256(tenantId + "\n" + maintenanceId);
    }

    static String stageOperationId(String operationId, String stage, int retryCount) {
        return "effect-" + sha256(operationId + "\n" + stage + "\n" + retryCount);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
        target.append('\n');
    }

    private static String requireHash(String value, String field) {
        String normalized = requireText(value, field).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) {
            throw validation(field + " 必须为 SHA-256");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field + " 不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeHash(String value) {
        return value == null || value.isBlank() ? null : requireHash(value, "billingResolutionResultHash");
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("PolicyMaintenanceApplicationPort", message);
    }
}
