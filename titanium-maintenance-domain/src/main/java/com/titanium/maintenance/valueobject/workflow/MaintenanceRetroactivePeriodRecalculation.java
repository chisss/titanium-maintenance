package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.Locale;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件级追溯期间重算状态机，分别保存 Product 与 Billing 检查点。 */
public record MaintenanceRetroactivePeriodRecalculation(
        String periodRecalculationId,
        int periodRecalculationVersion,
        String operationId,
        String requestHash,
        String analysisId,
        int analysisVersion,
        String analysisResultHash,
        MaintenanceRetroactivePeriodRecalculationStatus status,
        MaintenanceRetroactiveProductRecalculationEvidence productEvidence,
        MaintenanceRetroactiveBillingAdjustmentEvidence billingEvidence,
        String failureCode,
        String failureMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt) {

    public MaintenanceRetroactivePeriodRecalculation {
        periodRecalculationId = text(periodRecalculationId, "periodRecalculationId");
        operationId = text(operationId, "operationId");
        requestHash = hash(requestHash, "requestHash");
        analysisId = text(analysisId, "analysisId");
        analysisResultHash = hash(analysisResultHash, "analysisResultHash");
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (periodRecalculationVersion < 1 || analysisVersion < 1 || status == null
                || startedAt == null || updatedAt == null) {
            throw invalid("期间重算版本、分析版本、状态和时间不能为空");
        }
        validateState();
    }

    public static MaintenanceRetroactivePeriodRecalculation start(
            String id,
            int version,
            String operationId,
            String requestHash,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            LocalDateTime startedAt) {
        return new MaintenanceRetroactivePeriodRecalculation(
                id, version, operationId, requestHash, analysisId, analysisVersion, analysisResultHash,
                MaintenanceRetroactivePeriodRecalculationStatus.RECALCULATING, null, null,
                null, null, startedAt, null, startedAt);
    }

    public MaintenanceRetroactivePeriodRecalculation recordProduct(
            MaintenanceRetroactiveProductRecalculationEvidence evidence,
            LocalDateTime recordedAt) {
        if (productEvidence != null) {
            if (productEvidence.resultHash().equals(evidence.resultHash())) {
                return this;
            }
            throw invalid("同一期间重算已存在不同Product结果");
        }
        if (status != MaintenanceRetroactivePeriodRecalculationStatus.RECALCULATING
                && status != MaintenanceRetroactivePeriodRecalculationStatus.FAILED) {
            throw invalid("当前状态不能记录Product期间重算结果");
        }
        LocalDateTime time = resultTime(recordedAt);
        return new MaintenanceRetroactivePeriodRecalculation(
                periodRecalculationId, periodRecalculationVersion, operationId, requestHash,
                analysisId, analysisVersion, analysisResultHash,
                MaintenanceRetroactivePeriodRecalculationStatus.PRODUCT_COMPLETED, evidence, null,
                null, null, startedAt, null, time);
    }

    public MaintenanceRetroactivePeriodRecalculation completeBilling(
            MaintenanceRetroactiveBillingAdjustmentEvidence evidence,
            LocalDateTime completedTime) {
        if (billingEvidence != null) {
            if (billingEvidence.resultHash().equals(evidence.resultHash())) {
                return this;
            }
            throw invalid("同一期间重算已存在不同Billing结果");
        }
        if (productEvidence == null || (status != MaintenanceRetroactivePeriodRecalculationStatus.PRODUCT_COMPLETED
                && status != MaintenanceRetroactivePeriodRecalculationStatus.FAILED)) {
            throw invalid("必须先取得Product检查点才能记录Billing结果");
        }
        LocalDateTime time = resultTime(completedTime);
        return new MaintenanceRetroactivePeriodRecalculation(
                periodRecalculationId, periodRecalculationVersion, operationId, requestHash,
                analysisId, analysisVersion, analysisResultHash,
                evidence.requiresReview() ? MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED
                        : MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED,
                productEvidence, evidence, null, null, startedAt, time, time);
    }

    public MaintenanceRetroactivePeriodRecalculation fail(
            String code,
            String message,
            LocalDateTime failedAt) {
        if (status == MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                || status == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED) {
            throw invalid("已完成期间重算不能改为失败");
        }
        String normalizedCode = text(code, "failureCode");
        String normalizedMessage = text(message, "failureMessage");
        if (status == MaintenanceRetroactivePeriodRecalculationStatus.FAILED
                && normalizedCode.equals(failureCode) && normalizedMessage.equals(failureMessage)) {
            return this;
        }
        LocalDateTime time = resultTime(failedAt);
        return new MaintenanceRetroactivePeriodRecalculation(
                periodRecalculationId, periodRecalculationVersion, operationId, requestHash,
                analysisId, analysisVersion, analysisResultHash,
                MaintenanceRetroactivePeriodRecalculationStatus.FAILED, productEvidence, null,
                normalizedCode, normalizedMessage, startedAt, time, time);
    }

    public boolean sameRequest(String candidateOperationId, String candidateRequestHash) {
        return operationId.equals(text(candidateOperationId, "operationId"))
                && requestHash.equals(hash(candidateRequestHash, "requestHash"));
    }

    public boolean terminal() {
        return status == MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                || status == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED;
    }

    private void validateState() {
        if (status == MaintenanceRetroactivePeriodRecalculationStatus.RECALCULATING
                && (productEvidence != null || billingEvidence != null || failureCode != null
                        || failureMessage != null || completedAt != null)) {
            throw invalid("重算中状态不能携带检查点或终态信息");
        }
        if (status == MaintenanceRetroactivePeriodRecalculationStatus.PRODUCT_COMPLETED
                && (productEvidence == null || billingEvidence != null || failureCode != null
                        || failureMessage != null || completedAt != null)) {
            throw invalid("Product完成状态缺少Product检查点或携带非法信息");
        }
        if ((status == MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                || status == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED)
                && (productEvidence == null || billingEvidence == null || failureCode != null
                        || failureMessage != null || completedAt == null)) {
            throw invalid("期间重算终态缺少Product/Billing检查点");
        }
        if (status == MaintenanceRetroactivePeriodRecalculationStatus.FAILED
                && (billingEvidence != null || failureCode == null || failureMessage == null
                        || completedAt == null)) {
            throw invalid("期间重算失败状态必须只保留成功检查点和失败事实");
        }
    }

    private LocalDateTime resultTime(LocalDateTime time) {
        if (time == null || time.isBefore(startedAt)) {
            throw invalid("结果时间不能早于开始时间");
        }
        return time;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + "不能为空");
        }
        return value.trim();
    }

    private static String hash(String value, String field) {
        String result = text(value, field).toLowerCase(Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) {
            throw invalid(field + "必须为SHA-256");
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactivePeriodRecalculation", "recalculation", message);
    }
}
